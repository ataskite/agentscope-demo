# P4 Agentic RAG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a `rag-agent` using AgentScope's `RAGMode.AGENTIC` and enhance the debug panel to visualize RAG tool calls.

**Architecture:** Add a new YAML-only agent entry with `ragMode: agentic` (framework auto-injects `retrieve_knowledge` tool). Enhance `ObservabilityHook` to detect RAG tool calls and emit extra fields. Update frontend debug module to render RAG retrieval events with a distinct style.

**Tech Stack:** Java 17, Spring Boot 3.5, AgentScope 1.0.11, vanilla JS (ES6 modules)

---

### Task 1: Add `rag-agent` to agents.yml

**Files:**
- Modify: `src/main/resources/config/agents.yml` (after `rag-chat` block, ~line 201)

- [ ] **Step 1: Add the agent entry**

Insert the following YAML block after the `rag-chat` agent (after line 200, before the `vision-analyzer` agent):

```yaml

  - agentId: rag-agent
    category: single
    name: Agentic RAG 研究助手
    description: 自主决定何时检索知识库的研究助手。与 rag-chat 的区别：rag-chat 自动检索所有问题，而本 Agent 会判断问题是否需要检索知识库。
    systemPrompt: |
      你是一个研究助手，擅长根据用户问题自主判断是否需要从知识库中检索信息。

      判断规则：
      - 当用户的问题涉及专业知识、技术细节、或特定文档内容时，使用 retrieve_knowledge 工具检索
      - 当用户问的是常识性问题、日常闲聊、或通用编程知识时，直接回答，不需要检索
      - 如果不确定是否需要检索，优先检索

      检索到内容后，基于检索结果回答，并标注引用来源。
      如果检索结果与问题不相关，说明知识库中没有相关信息，基于自身知识回答。
    modelName: qwen-plus
    streaming: true
    enableThinking: true
    autoContext: true
    autoContextMsgThreshold: 30
    autoContextLastKeep: 10
    autoContextTokenRatio: 0.3
    ragEnabled: true
    ragMode: agentic
    ragRetrieveLimit: 5
    ragScoreThreshold: 0.3
    skills: []
    userTools: []
    systemTools: []
    samplePrompts:
      - prompt: "你好，介绍一下你自己"
        expectedBehavior: "不调用 retrieve_knowledge，直接回答"
      - prompt: "知识库里有没有关于光模块技术的讨论？"
        expectedBehavior: "调用 retrieve_knowledge 检索知识库，基于检索结果回答"
      - prompt: "什么是 Java？"
        expectedBehavior: "判断为常识问题，不检索知识库，直接回答"
```

- [ ] **Step 2: Verify the YAML is valid**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS (Spring Boot parses agents.yml at startup; compile validates the Java side)

- [ ] **Step 3: Start the app and verify agent appears**

Run: `mvn spring-boot:run`

Open http://localhost:8080 and check:
1. Agent list includes "Agentic RAG 研究助手" under Single Agent category
2. Selecting it shows the correct description
3. Sample prompts appear

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/config/agents.yml
git commit -m "feat: add rag-agent with RAGMode.AGENTIC

New agent that autonomously decides when to retrieve from the knowledge
base using the retrieve_knowledge tool, contrasting with rag-chat's
automatic Generic RAG mode."
```

---

### Task 2: Enhance ObservabilityHook for RAG tool call detection

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/hook/ObservabilityHook.java` (lines 246-284, `handlePostActing` method)

- [ ] **Step 1: Add RAG detection logic to `handlePostActing`**

In `ObservabilityHook.java`, replace the `handlePostActing` method body with the following. The change adds RAG-specific fields (`isRagRetrieval`, `ragQuery`, `ragHitCount`, `ragScoreRange`) when the tool name is `retrieve_knowledge`.

Replace the entire `handlePostActing` method (lines 246-284) with:

```java
    // ---- PostActing: tool execution ends ----
    private void handlePostActing(PostActingEvent e) {
        ToolUseBlock toolUse = e.getToolUse();
        ToolResultBlock result = e.getToolResult();
        String toolId = toolUse.getId() != null ? toolUse.getId() : "tool-unknown";

        Long startNanos = toolStartNanos.remove(toolId);
        long durationMs = startNanos != null ? (System.nanoTime() - startNanos) / 1_000_000 : -1;

        String resultText = "";
        if (result != null && result.getOutput() != null) {
            resultText = result.getOutput().stream()
                    .filter(o -> o instanceof TextBlock)
                    .map(o -> ((TextBlock) o).getText())
                    .reduce("", (a, b) -> a + b);
        }

        String resultPreview = resultText.length() > 200
                ? resultText.substring(0, 200) + "..."
                : resultText;

        boolean isSuccess = resultText.isEmpty() || !resultText.startsWith("Error");
        String toolName = toolUse.getName() != null ? toolUse.getName() : "";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", toolId);
        data.put("name", toolName);
        data.put("result", resultText);
        data.put("resultPreview", resultPreview);
        data.put("duration_ms", durationMs);
        data.put("success", isSuccess);
        data.put("timestamp", System.currentTimeMillis());

        if ("load_skill_through_path".equals(toolName)) {
            data.put("isSkill", true);
        }

        // RAG retrieval detection
        if ("retrieve_knowledge".equals(toolName)) {
            data.put("isRagRetrieval", true);
            String query = extractToolParam(toolUse.getInput() != null ? toolUse.getInput().toString() : "", "query");
            data.put("ragQuery", query);
            int[] hitScore = parseRagResultStats(resultText);
            data.put("ragHitCount", hitScore[0]);
            if (hitScore[1] >= 0 && hitScore[2] >= 0) {
                data.put("ragScoreRange", String.format("%.2f-%.2f", hitScore[1] / 100.0, hitScore[2] / 100.0));
            }
        }

        log.info("[tool_end] {} durationMs={} success={}", toolName, durationMs, isSuccess);
        emit("tool_end", data);
    }
```

- [ ] **Step 2: Add helper methods for RAG parsing**

Add these two private methods at the end of the class, before the `emit` method (before line 417):

```java
    /** Extract a parameter value from tool input string like "{query=some text, limit=5}" */
    private String extractToolParam(String paramsStr, String paramName) {
        if (paramsStr == null) return "";
        String prefix = paramName + "=";
        int idx = paramsStr.indexOf(prefix);
        if (idx < 0) return "";
        String sub = paramsStr.substring(idx + prefix.length());
        int end = sub.indexOf(',');
        if (end < 0) end = sub.indexOf('}');
        if (end < 0) end = sub.length();
        return sub.substring(0, end).trim();
    }

    /**
     * Parse RAG result statistics from the retrieve_knowledge tool output.
     * Returns [hitCount, minScore, maxScore]. Scores are in 0-100 range from AgentScope.
     * If no scores found, returns [0, -1, -1].
     */
    private int[] parseRagResultStats(String resultText) {
        if (resultText == null || resultText.isEmpty()) return new int[]{0, -1, -1};

        int count = 0;
        int minScore = Integer.MAX_VALUE;
        int maxScore = Integer.MIN_VALUE;

        // AgentScope returns relevance scores as integers or decimals in the result text
        // Pattern: "relevance score: 85" or "score: 0.85" or "Relevance: 85%"
        java.util.regex.Pattern scorePattern = java.util.regex.Pattern.compile(
                "(?:relevance|score|相关性)\\s*[:：]?\\s*(\\d+)(?:\\.\\d+)?(?:%)?",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = scorePattern.matcher(resultText);
        while (matcher.find()) {
            int score = Integer.parseInt(matcher.group(1));
            // Normalize percentage-style scores (e.g., 85 means 85%)
            if (score > 100) score = score / 10; // handle cases like 850 meaning 85%
            minScore = Math.min(minScore, score);
            maxScore = Math.max(maxScore, score);
            count++;
        }

        // Fallback: count document entries if no scores found
        if (count == 0) {
            // Count lines starting with [1], [2], etc. or "Document" markers
            java.util.regex.Pattern docPattern = java.util.regex.Pattern.compile("^\\s*\\[\\d+\\]", java.util.regex.Pattern.MULTILINE);
            java.util.regex.Matcher docMatcher = docPattern.matcher(resultText);
            while (docMatcher.find()) count++;
            return new int[]{count, -1, -1};
        }

        return new int[]{count, minScore, maxScore};
    }
```

- [ ] **Step 3: Compile and verify**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/skloda/agentscope/hook/ObservabilityHook.java
git commit -m "feat: detect retrieve_knowledge tool calls in ObservabilityHook

Add isRagRetrieval, ragQuery, ragHitCount, and ragScoreRange fields
to tool_end events when the tool is retrieve_knowledge."
```

---

### Task 3: Enhance frontend debug panel for RAG tool rendering

**Files:**
- Modify: `src/main/resources/static/scripts/modules/debug.js` (add RAG icon)
- Modify: `src/main/resources/static/scripts/chat.js` (tool_start and tool_end handlers)
- Modify: `src/main/resources/static/styles/modules/debug.css` (RAG styling)

- [ ] **Step 1: Add RAG timeline icon in debug.js**

In `debug.js`, add a `'rag'` case to the `getTimelineIcon` function (line 166-175). Replace the function:

```javascript
function getTimelineIcon(type) {
    switch (type) {
        case 'phase': return '◆';
        case 'llm': return '↻';
        case 'memory': return '◈';
        case 'skill': return '◉';
        case 'tool': return '⚡';
        case 'rag': return '🔍';
        case 'error': return '⚠';
        default: return '·';
    }
}
```

- [ ] **Step 2: Update tool_start handler in chat.js for RAG detection**

In `chat.js`, find the `tool_start` case (around line 237). After the line `var isSkill = payload.isSkill === true;` (line 245), add RAG detection:

Replace lines 245-261 (from `var isSkill = ...` through `updateRoundMetrics();`):

```javascript
                            var isSkill = payload.isSkill === true;
                            var isRag = payload.name === 'retrieve_knowledge';
                            var tSkillName = payload.displayName || '';

                            if (enableThinking) {
                                if (isRag) {
                                    var ragQuery = '';
                                    try {
                                        var params = JSON.parse(payload.params || '{}');
                                        ragQuery = params.query || '';
                                    } catch(e) {
                                        ragQuery = payload.paramsPreview || '';
                                    }
                                    updateThinkingBox('🔍 RAG检索: ' + ragQuery, fileInfo);
                                } else if (isSkill) {
                                    updateThinkingBox('📖 Loading skill: ' + (tSkillName || '...'), fileInfo);
                                } else {
                                    updateThinkingBox('⚡ ' + tName + '(' + tParamsPreview + ')', fileInfo);
                                }
                            }

                            if (currentRound) {
                                currentRound.toolCallCount++;
                                currentRound._currentToolStart = Date.now();
                                var rowType = isRag ? 'rag' : (isSkill ? 'skill' : 'tool');
                                var rowLabel = isRag ? 'RAG → retrieve_knowledge' : (isSkill ? ('Skill → ' + (tSkillName || '')) : ('Tool → ' + tName));
                                currentRound._currentToolRow = addTimelineRow(rowType, rowLabel, '...', 'running');
                                currentRound._currentToolIsSkill = isSkill;
                                currentRound._currentToolIsRag = isRag;
                                updateRoundMetrics();
```

- [ ] **Step 3: Update tool_end handler in chat.js for RAG display**

In `chat.js`, find the `tool_end` case (around line 269). After the existing tool_end result handling, enhance the row update to show RAG details when applicable.

Replace the block from `if (targetRound._currentToolRow) {` through the closing `}` before the else warn (approximately lines 286-295):

```javascript
                                if (targetRound._currentToolRow) {
                                    var tRow = targetRound._currentToolRow;
                                    var tmEl = tRow.querySelector('.rtl-metrics');
                                    var tsEl = tRow.querySelector('.rtl-status');
                                    var durStr = teDurMs >= 0 ? formatDuration(teDurMs) : '';
                                    if (tmEl) {
                                        if (targetRound._currentToolIsRag && payload.isRagRetrieval) {
                                            var ragInfo = (payload.ragHitCount || 0) + ' hits';
                                            if (payload.ragScoreRange) ragInfo += ' (' + payload.ragScoreRange + ')';
                                            tmEl.textContent = ragInfo;
                                            tRow.title = 'Query: ' + (payload.ragQuery || '');
                                        } else {
                                            tmEl.textContent = durStr;
                                        }
                                    }
                                    if (tsEl) tsEl.textContent = teDurMs >= 0 ? '✓' : '✗';
                                    tRow.classList.remove('status-running');
                                    tRow.classList.add(teDurMs >= 0 ? 'status-ok' : 'status-fail');
                                    targetRound._currentToolRow = null;
```

- [ ] **Step 4: Add RAG styling to debug.css**

Add the following CSS rules at the end of `debug.css`:

```css
/* ===== RAG RETRIEVAL ===== */
.rtl-row.type-rag {
    background: rgba(0, 240, 255, 0.05);
    border-radius: 4px;
    margin: 2px 0;
    padding: var(--space-xs) 0;
}

.rtl-row.type-rag .rtl-icon {
    color: var(--neon-cyan);
    text-shadow: 0 0 8px rgba(0, 240, 255, 0.7), 0 0 15px rgba(0, 240, 255, 0.3);
    font-size: 12px;
}

.rtl-row.type-rag .rtl-label {
    color: var(--neon-cyan);
    font-weight: 500;
}

.rtl-row.type-rag .rtl-metrics {
    color: var(--neon-green);
    font-size: 10px;
}
```

- [ ] **Step 5: Verify frontend loads without errors**

Run: `mvn spring-boot:run`

Open http://localhost:8080, open browser DevTools Console. Verify:
1. No JavaScript errors
2. Debug panel renders correctly

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/scripts/modules/debug.js src/main/resources/static/scripts/chat.js src/main/resources/static/styles/modules/debug.css
git commit -m "feat: render RAG retrieval tool calls distinctly in debug panel

Show RAG icon (🔍), hit count, and score range for retrieve_knowledge
tool calls. Differentiates from regular tool calls in the timeline."
```

---

### Task 4: Smoke test and verify behavior

**Files:** None (manual verification)

- [ ] **Step 1: Start the application**

Run: `mvn spring-boot:run`

- [ ] **Step 2: Test common-sense question (no retrieval)**

1. Select "Agentic RAG 研究助手" agent
2. Send: "你好，介绍一下你自己"
3. Verify: Agent responds directly, debug panel shows NO `retrieve_knowledge` tool call
4. Verify: Debug panel shows thinking (agent reasoning about whether to retrieve)

- [ ] **Step 3: Test knowledge-base question (with retrieval)**

1. Send: "知识库里有没有关于光模块技术的讨论？"
2. Verify: Debug panel shows `RAG → retrieve_knowledge` with 🔍 icon
3. Verify: Tool call shows hit count and score range in timeline
4. Verify: Agent response references retrieved content

- [ ] **Step 4: Compare with rag-chat**

1. Select "RAG Chat" agent
2. Send the same question: "知识库里有没有关于光模块技术的讨论？"
3. Verify: Debug panel does NOT show any retrieve_knowledge tool call (Generic mode uses hook, invisible to debug)
4. Verify: Both agents answer the question, but the mechanism is visibly different

- [ ] **Step 5: Verify all existing tests still pass**

Run: `mvn test`
Expected: All tests pass (158+ tests)

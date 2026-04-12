# Debug Panel Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the thinking-text-dump debug panel with a structured observability view showing token usage, tool timing, and session summaries.

**Architecture:** Backend (`AgentService.java`) extracts `ChatUsage` from AgentScope events and tracks tool call durations, emitting new SSE event types (`usage`, enhanced `tool_call`/`tool_result`, enhanced `done`). Frontend (`chat.html`) replaces the text-scroll debug panel with a fixed summary card + compact event timeline.

**Tech Stack:** Spring Boot SSE, AgentScope 1.0.11 (`ChatUsage`, `ToolUseBlock.getId()`), vanilla JS.

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `src/main/java/com/msxf/agentscope/service/AgentService.java` | Modify | Extract ChatUsage, track tool durations, emit new SSE events |
| `src/main/resources/templates/chat.html` | Modify | New debug panel CSS (summary card + timeline), new JS state/handlers |

No new files needed. No new endpoints. All data flows through the existing SSE connection.

---

### Task 1: Backend — Extract usage data and track tool durations

**Files:**
- Modify: `src/main/java/com/msxf/agentscope/service/AgentService.java`

The current `handleEvent()` processes `ThinkingBlock`, `TextBlock`, `ToolUseBlock`, `ToolResultBlock`. We need to:
1. Track tool call start times by `ToolUseBlock.getId()`
2. On `ToolResultBlock`, calculate duration from the tracked start time
3. After processing all blocks in an event, check `Msg.getChatUsage()` and emit a `usage` event
4. Stop emitting `thinking` events (thinking content stays in the chat-area thinking box only)
5. Enhance `tool_call` event with `timestamp` and `id`
6. Enhance `tool_result` event with `duration_ms`
7. Enhance `done` event with session totals

- [ ] **Step 1: Replace the full `handleEvent` method and add tracking fields**

Add these fields to `AgentService` class (after the `agents` field, around line 26):

```java
    private final ConcurrentHashMap<String, Long> toolCallStartTimes = new ConcurrentHashMap<>();
```

Replace the entire `handleEvent` method (lines 87-122) with:

```java
    private int llmCallCount = 0;

    private void handleEvent(Event event, SseEmitter emitter) throws Exception {
        Msg msg = event.getMessage();
        if (msg == null || msg.getContent() == null) {
            return;
        }

        for (ContentBlock block : msg.getContent()) {
            if (block instanceof ThinkingBlock tb) {
                // Send to chat-area thinking box only, not to debug panel
                String thinking = tb.getThinking();
                if (thinking != null && !thinking.isEmpty()) {
                    sendEvent(emitter, "thinking", Map.of("content", thinking));
                }
            } else if (block instanceof TextBlock tb) {
                String text = tb.getText();
                if (text != null && !text.isEmpty()) {
                    sendEvent(emitter, "text", Map.of("content", text));
                }
            } else if (block instanceof ToolUseBlock tub) {
                String toolId = tub.getId();
                toolCallStartTimes.put(toolId, System.currentTimeMillis());
                sendEvent(emitter, "tool_call", Map.of(
                        "id", toolId != null ? toolId : "",
                        "name", tub.getName() != null ? tub.getName() : "",
                        "params", tub.getInput() != null ? tub.getInput().toString() : "{}",
                        "timestamp", System.currentTimeMillis()
                ));
            } else if (block instanceof ToolResultBlock trb) {
                String resultText = trb.getOutput() != null
                        ? trb.getOutput().stream()
                        .filter(o -> o instanceof TextBlock)
                        .map(o -> ((TextBlock) o).getText())
                        .reduce("", (a, b) -> a + b)
                        : "";
                // Calculate duration
                String toolId = trb.getId();
                Long startTime = toolId != null ? toolCallStartTimes.remove(toolId) : null;
                long durationMs = startTime != null ? System.currentTimeMillis() - startTime : -1;

                // Truncate result for debug preview (max 200 chars)
                String resultPreview = resultText.length() > 200
                        ? resultText.substring(0, 200) + "..."
                        : resultText;

                sendEvent(emitter, "tool_result", Map.of(
                        "id", toolId != null ? toolId : "",
                        "name", trb.getName() != null ? trb.getName() : "",
                        "result", resultText,
                        "result_preview", resultPreview,
                        "duration_ms", durationMs,
                        "timestamp", System.currentTimeMillis()
                ));
            }
        }

        // Extract usage data from the message (available on last chunk of reasoning events)
        if (event.isLast()) {
            ChatUsage usage = msg.getChatUsage();
            if (usage != null) {
                llmCallCount++;
                sendEvent(emitter, "usage", Map.of(
                        "inputTokens", usage.getInputTokens(),
                        "outputTokens", usage.getOutputTokens(),
                        "totalTokens", usage.getTotalTokens(),
                        "time", usage.getTime(),
                        "callNumber", llmCallCount
                ));
            }
        }
    }
```

- [ ] **Step 2: Add ChatUsage import**

Add this import at the top of `AgentService.java` (around line 8, with the other agentscope imports):

```java
import io.agentscope.core.model.ChatUsage;
```

- [ ] **Step 3: Enhance the `done` handler to include session summary**

In the `streamToEmitter` method, replace the `onComplete` lambda (lines 74-84):

```java
                        () -> {
                            try {
                                Map<String, Object> doneData = new java.util.LinkedHashMap<>();
                                doneData.put("type", "done");
                                doneData.put("totalLlmCalls", llmCallCount);
                                doneData.put("toolCallsRemaining", toolCallStartTimes.size());
                                String json = objectMapper.writeValueAsString(doneData);
                                emitter.send(SseEmitter.event().name("message").data(json));
                            } catch (Exception e) {
                                log.error("Error sending done event", e);
                                emitter.completeWithError(e);
                                return;
                            }
                            emitter.complete();
                            // Reset per-session counters
                            llmCallCount = 0;
                            toolCallStartTimes.clear();
                        }
```

- [ ] **Step 4: Build and verify compilation**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/msxf/agentscope/service/AgentService.java
git commit -m "feat: emit usage/timing SSE events from AgentService for debug observability"
```

---

### Task 2: Frontend CSS — Replace debug panel styles

**Files:**
- Modify: `src/main/resources/templates/chat.html` (CSS section)

Replace the old debug panel CSS with new styles for the summary card and compact timeline.

- [ ] **Step 1: Replace all debug panel CSS**

Find the CSS block starting at `/* ===== DEBUG PANEL ===== */` (around line 1040) and ending just before `/* ===== ERROR MESSAGE ===== */` (around line 1262). Replace the entire block with:

```css
        /* ===== DEBUG PANEL ===== */
        .debug-panel {
            width: 320px;
            min-width: 320px;
            background: rgba(10, 10, 18, 0.95);
            border-left: 1px solid var(--border-dim);
            display: flex;
            flex-direction: column;
            transition: width 0.3s ease, min-width 0.3s ease;
            overflow: hidden;
        }

        .debug-panel.collapsed {
            width: 32px;
            min-width: 32px;
        }

        .debug-header {
            height: 40px;
            min-height: 40px;
            display: flex;
            align-items: center;
            padding: 0 var(--space-sm);
            gap: var(--space-sm);
            border-bottom: 1px solid var(--border-dim);
            flex-shrink: 0;
            background: rgba(0, 0, 0, 0.3);
        }

        .debug-title {
            font-family: var(--font-heading);
            font-size: 10px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 2px;
            color: var(--neon-magenta);
            white-space: nowrap;
            overflow: hidden;
            text-shadow: 0 0 8px rgba(255, 16, 240, 0.5);
        }

        .debug-toggle {
            width: 24px;
            height: 24px;
            background: none;
            border: 1px solid var(--border-dim);
            color: var(--text-muted);
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 10px;
            transition: all 0.2s ease;
            flex-shrink: 0;
            padding: 0;
            line-height: 1;
        }

        .debug-toggle:hover {
            border-color: var(--neon-magenta);
            color: var(--neon-magenta);
            box-shadow: 0 0 10px rgba(255, 16, 240, 0.3);
        }

        .debug-panel.collapsed .debug-header {
            writing-mode: vertical-lr;
            text-orientation: mixed;
            height: auto;
            padding: var(--space-sm) 6px;
            justify-content: flex-start;
        }

        .debug-panel.collapsed .debug-title {
            font-size: 10px;
            letter-spacing: 3px;
        }

        .debug-panel.collapsed .debug-toggle {
            writing-mode: horizontal-tb;
        }

        /* ===== SUMMARY CARD ===== */
        .debug-summary {
            flex-shrink: 0;
            padding: 8px 10px;
            border-bottom: 1px solid var(--border-dim);
            background: rgba(0, 0, 0, 0.4);
        }

        .debug-summary-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 2px 0;
            font-size: 10px;
        }

        .debug-summary-label {
            color: var(--text-muted);
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            min-width: 50px;
        }

        .debug-summary-value {
            font-family: var(--font-body);
            color: var(--text-primary);
        }

        .debug-summary-value .token-in {
            color: var(--neon-blue);
        }

        .debug-summary-value .token-out {
            color: var(--neon-cyan);
        }

        .debug-summary-value .arrow {
            color: var(--text-muted);
            margin: 0 4px;
        }

        .debug-panel.collapsed .debug-summary {
            display: none;
        }

        /* ===== TIMELINE ===== */
        .debug-timeline {
            flex: 1;
            overflow-y: auto;
            padding: var(--space-xs);
            display: flex;
            flex-direction: column;
            gap: 2px;
            overflow-anchor: none;
            transform: translateZ(0);
        }

        .debug-panel.collapsed .debug-timeline {
            display: none;
        }

        .debug-timeline::-webkit-scrollbar {
            width: 4px;
        }

        .debug-timeline::-webkit-scrollbar-track {
            background: transparent;
        }

        .debug-timeline::-webkit-scrollbar-thumb {
            background: var(--border-dim);
            border-radius: 2px;
        }

        .debug-tl-row {
            display: flex;
            align-items: center;
            font-family: var(--font-body);
            font-size: 10px;
            line-height: 1.4;
            padding: 3px 6px;
            background: rgba(0, 0, 0, 0.2);
            border-left: 2px solid transparent;
            animation: debugIn 0.15s ease-out;
            will-change: opacity;
        }

        @keyframes debugIn {
            from { opacity: 0; }
            to { opacity: 1; }
        }

        .debug-tl-row.type-llm {
            color: var(--neon-purple);
            border-left-color: var(--neon-purple);
        }

        .debug-tl-row.type-tool {
            color: #FFB800;
            border-left-color: #FFB800;
            background: rgba(255, 184, 0, 0.06);
        }

        .debug-tl-row.type-tool.status-ok {
            border-left-color: var(--neon-green);
        }

        .debug-tl-row.type-error {
            color: var(--status-error);
            border-left-color: var(--status-error);
            background: rgba(255, 71, 87, 0.08);
        }

        .debug-tl-row.type-done {
            color: var(--neon-green);
            border-left-color: var(--neon-green);
        }

        .debug-tl-icon {
            width: 14px;
            flex-shrink: 0;
            text-align: center;
        }

        .debug-tl-label {
            flex: 1;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            padding-right: 6px;
        }

        .debug-tl-metrics {
            flex-shrink: 0;
            text-align: right;
            white-space: nowrap;
            color: var(--text-secondary);
        }

        .debug-tl-row.type-llm .debug-tl-metrics {
            color: var(--neon-purple);
            opacity: 0.8;
        }

        .debug-tl-row.type-tool .debug-tl-metrics {
            color: #FFB800;
            opacity: 0.8;
        }

        .debug-tl-status {
            flex-shrink: 0;
            width: 14px;
            text-align: center;
        }

        /* ===== CLEAR BUTTON ===== */
        .debug-clear {
            padding: var(--space-sm);
            border-top: 1px solid var(--border-dim);
            flex-shrink: 0;
        }

        .debug-clear button {
            width: 100%;
            padding: var(--space-sm) var(--space-md);
            background: none;
            border: 1px solid var(--border-dim);
            color: var(--text-muted);
            font-size: 10px;
            font-family: var(--font-heading);
            letter-spacing: 1px;
            text-transform: uppercase;
            cursor: pointer;
            transition: all 0.2s ease;
        }

        .debug-clear button:hover {
            border-color: var(--neon-magenta);
            color: var(--neon-magenta);
            box-shadow: 0 0 10px rgba(255, 16, 240, 0.2);
        }

        .debug-panel.collapsed .debug-clear {
            display: none;
        }
```

- [ ] **Step 2: Verify no visual regressions in non-debug areas**

The only CSS changed is inside the debug panel block. The chat area, sidebar, messages, file upload styles are untouched.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/chat.html
git commit -m "feat: replace debug panel text-scroll CSS with summary card and timeline styles"
```

---

### Task 3: Frontend HTML — Update debug panel structure

**Files:**
- Modify: `src/main/resources/templates/chat.html` (HTML section)

Replace the debug panel HTML structure to add the summary card container.

- [ ] **Step 1: Replace the debug panel aside element**

Find this HTML block (around line 1693):

```html
            <aside class="debug-panel" id="debugPanel">
                <div class="debug-header">
                    <div class="debug-title">System Log</div>
                    <button class="debug-toggle" id="debugToggle" onclick="toggleDebug()" title="Toggle debug panel">▶</button>
                </div>
                <div class="debug-log" id="debugLog"></div>
                <div class="debug-clear">
                    <button onclick="clearDebug()">CLEAR LOG</button>
                </div>
            </aside>
```

Replace with:

```html
            <aside class="debug-panel" id="debugPanel">
                <div class="debug-header">
                    <div class="debug-title">Runtime</div>
                    <button class="debug-toggle" id="debugToggle" onclick="toggleDebug()" title="Toggle debug panel">▶</button>
                </div>
                <div class="debug-summary" id="debugSummary">
                    <div class="debug-summary-row">
                        <span class="debug-summary-label">Tokens</span>
                        <span class="debug-summary-value"><span class="token-in" id="sumInputTok">0</span><span class="arrow">→</span><span class="token-out" id="sumOutputTok">0</span></span>
                    </div>
                    <div class="debug-summary-row">
                        <span class="debug-summary-label">Time</span>
                        <span class="debug-summary-value" id="sumTotalTime">0.0s</span>
                    </div>
                    <div class="debug-summary-row">
                        <span class="debug-summary-label">Tools</span>
                        <span class="debug-summary-value" id="sumToolCalls">0 calls</span>
                    </div>
                    <div class="debug-summary-row">
                        <span class="debug-summary-label">LLM</span>
                        <span class="debug-summary-value" id="sumLlmCalls">0 calls</span>
                    </div>
                </div>
                <div class="debug-timeline" id="debugTimeline"></div>
                <div class="debug-clear">
                    <button onclick="clearDebug()">CLEAR</button>
                </div>
            </aside>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/chat.html
git commit -m "feat: add debug panel summary card and timeline HTML structure"
```

---

### Task 4: Frontend JS — New state model and event handlers

**Files:**
- Modify: `src/main/resources/templates/chat.html` (JS section)

Replace the debug panel JavaScript: new state model, new SSE event handlers, new render functions. Remove old `addDebugEntry` function.

- [ ] **Step 1: Replace DOM references and add session state**

Find the DOM REFERENCES block (around line 1735-1744):

```javascript
        /* ===== DOM REFERENCES ===== */
        const chatMessages = document.getElementById('chatMessages');
        const messageInput = document.getElementById('messageInput');
        const sendBtn = document.getElementById('sendBtn');
        const chatEmpty = document.getElementById('chatEmpty');
        const chatHeaderName = document.getElementById('chatHeaderName');
        const chatHeaderDesc = document.getElementById('chatHeaderDesc');
        const debugPanel = document.getElementById('debugPanel');
        const debugLog = document.getElementById('debugLog');
        const debugToggle = document.getElementById('debugToggle');
```

Replace with:

```javascript
        /* ===== DOM REFERENCES ===== */
        const chatMessages = document.getElementById('chatMessages');
        const messageInput = document.getElementById('messageInput');
        const sendBtn = document.getElementById('sendBtn');
        const chatEmpty = document.getElementById('chatEmpty');
        const chatHeaderName = document.getElementById('chatHeaderName');
        const chatHeaderDesc = document.getElementById('chatHeaderDesc');
        const debugPanel = document.getElementById('debugPanel');
        const debugTimeline = document.getElementById('debugTimeline');
        const debugToggle = document.getElementById('debugToggle');

        /* ===== OBSERVABILITY STATE ===== */
        var sessionSummary = {
            inputTokens: 0,
            outputTokens: 0,
            totalTime: 0,
            toolCallCount: 0,
            llmCallCount: 0
        };
```

- [ ] **Step 2: Replace the entire `addDebugEntry` function and `clearDebug`**

Find and replace the `addDebugEntry` function (starts around line 2384) and `clearDebug` function:

```javascript
        function addDebugEntry(type, content) {
            var entry = document.createElement('div');
            entry.className = 'debug-entry type-' + type;
            // ... whole function body
        }

        function clearDebug() {
            debugLog.innerHTML = '';
        }
```

Replace both functions with:

```javascript
        function addTimelineRow(type, label, metrics, status) {
            var row = document.createElement('div');
            row.className = 'debug-tl-row type-' + type;
            if (status) {
                row.classList.add('status-' + status);
            }
            row.innerHTML =
                '<span class="debug-tl-icon">' + getTimelineIcon(type) + '</span>' +
                '<span class="debug-tl-label">' + escapeHtml(label) + '</span>' +
                '<span class="debug-tl-metrics">' + (metrics || '') + '</span>' +
                '<span class="debug-tl-status">' + (status === 'ok' ? '✓' : status === 'fail' ? '✗' : '') + '</span>';
            debugTimeline.appendChild(row);
            requestAnimationFrame(function() {
                debugTimeline.scrollTop = debugTimeline.scrollHeight;
            });
        }

        function getTimelineIcon(type) {
            switch (type) {
                case 'llm': return '↻';
                case 'tool': return '⚡';
                case 'error': return '⚠';
                case 'done': return '◉';
                default: return '·';
            }
        }

        function updateSummaryCard() {
            document.getElementById('sumInputTok').textContent = sessionSummary.inputTokens.toLocaleString();
            document.getElementById('sumOutputTok').textContent = sessionSummary.outputTokens.toLocaleString();
            document.getElementById('sumTotalTime').textContent = sessionSummary.totalTime.toFixed(1) + 's';
            document.getElementById('sumToolCalls').textContent = sessionSummary.toolCallCount + ' calls';
            document.getElementById('sumLlmCalls').textContent = sessionSummary.llmCallCount + ' calls';
        }

        function resetSessionSummary() {
            sessionSummary = {
                inputTokens: 0,
                outputTokens: 0,
                totalTime: 0,
                toolCallCount: 0,
                llmCallCount: 0
            };
            updateSummaryCard();
        }

        function clearDebug() {
            debugTimeline.innerHTML = '';
            resetSessionSummary();
        }
```

- [ ] **Step 3: Update SSE event handlers in `sendMessage`**

In the `sendMessage` function, find the `switch (payload.type)` block (starts around line 2111). Replace the entire switch block contents:

Replace the `case 'thinking':` block:

```javascript
                        case 'thinking':
                            var thinkingText = payload.content || payload.message || 'Processing...';
                            updateThinkingBox(thinkingText, fileInfo);
                            // No longer pushing to debug panel
                            break;
```

Replace the `case 'tool_call':` block:

```javascript
                        case 'tool_call':
                            var toolName = payload.name || 'unknown';
                            var toolParams = payload.params || '{}';
                            var toolText = 'Calling ' + toolName + ' with params: ' + toolParams;
                            updateThinkingBox('🔧 ' + toolText, fileInfo);
                            addTimelineRow('tool', toolName, '...', '');
                            break;
```

Replace the `case 'tool_result':` block:

```javascript
                        case 'tool_result':
                            var toolName = payload.name || 'unknown';
                            var toolResult = payload.result || '';
                            var durationMs = payload.duration_ms || -1;
                            var displayResult = toolResult.length > 100 ? toolResult.substring(0, 100) + '...' : toolResult;
                            var resultText = toolName + ' returned: ' + displayResult;
                            updateThinkingBox('✓ ' + resultText, fileInfo);
                            // Update last timeline row with duration and status
                            var lastToolRow = debugTimeline.querySelector('.debug-tl-row.type-tool:last-child');
                            if (lastToolRow) {
                                var durationStr = durationMs >= 0 ? (durationMs >= 1000 ? (durationMs / 1000).toFixed(1) + 's' : durationMs + 'ms') : '';
                                lastToolRow.querySelector('.debug-tl-metrics').textContent = durationStr;
                                lastToolRow.classList.add(durationMs >= 0 ? 'status-ok' : 'status-fail');
                            }
                            sessionSummary.toolCallCount++;
                            updateSummaryCard();
                            break;
```

Add a new `case 'usage':` block right after the `case 'tool_result':` block:

```javascript
                        case 'usage':
                            sessionSummary.inputTokens += payload.inputTokens || 0;
                            sessionSummary.outputTokens += payload.outputTokens || 0;
                            sessionSummary.totalTime += payload.time || 0;
                            sessionSummary.llmCallCount++;
                            updateSummaryCard();
                            var callNum = payload.callNumber || sessionSummary.llmCallCount;
                            var tokStr = (payload.totalTokens || 0).toLocaleString() + ' tok';
                            var timeStr = payload.time ? payload.time.toFixed(1) + 's' : '';
                            addTimelineRow('llm', 'LLM call #' + callNum, tokStr + '  ' + timeStr, '');
                            break;
```

Replace the `case 'done':` block:

```javascript
                        case 'done':
                            completeThinkingBox();
                            currentEventSource.close();
                            currentEventSource = null;
                            isStreaming = false;
                            setStreamingState(false);
                            addTimelineRow('done', 'Complete', sessionSummary.totalTime.toFixed(1) + 's', 'ok');
                            scrollToBottom(chatMessages);
                            break;
```

Replace the `case 'error':` block:

```javascript
                        case 'error':
                            completeThinkingBox();
                            if (!agentBubble) {
                                agentBubble = addAgentBubble();
                            }
                            agentBubble.classList.remove('md-render');
                            agentBubble.style.whiteSpace = 'pre-wrap';
                            agentBubble.textContent += '\n\n[ERROR] ' + (payload.message || payload.content || 'Unknown error');
                            agentBubble.closest('.message').classList.add('error');
                            addTimelineRow('error', payload.message || payload.content || 'Unknown error', '', 'fail');
                            currentEventSource.close();
                            currentEventSource = null;
                            isStreaming = false;
                            setStreamingState(false);
                            scrollToBottom(chatMessages);
                            break;
```

Also remove the `addDebugEntry` call from the onerror handler. Replace:

```javascript
                        addDebugEntry('error', '[NETWORK] SSE connection terminated');
```

with:

```javascript
                        addTimelineRow('error', 'SSE connection terminated', '', 'fail');
```

And remove the `addDebugEntry` calls in the catch block. Replace:

```javascript
                addDebugEntry('error', '[NETWORK] ' + err.message);
```

with:

```javascript
                addTimelineRow('error', err.message, '', 'fail');
```

Also remove the initial `addDebugEntry` call at the start of `sendMessage`. Replace:

```javascript
            addDebugEntry('thinking', '>>> Sending to ' + currentAgent.toUpperCase() + ' agent...');
```

with:

```javascript
            // (removed thinking debug entry — debug panel now shows structured observability)
```

Also remove `addDebugEntry` calls in the error handling within sendMessage. Replace:

```javascript
                    addDebugEntry('error', '[ERROR] ' + data.error);
```

with:

```javascript
                    addTimelineRow('error', data.error, '', 'fail');
```

And:

```javascript
                    addDebugEntry('error', '[PARSE ERROR] ' + event.data);
```

with:

```javascript
                    addTimelineRow('error', 'Parse error', '', 'fail');
```

- [ ] **Step 4: Update `clearSession` to reset the new debug panel**

In the `clearSession` function, replace:

```javascript
            debugLog.innerHTML = '';
```

with:

```javascript
            debugTimeline.innerHTML = '';
            resetSessionSummary();
```

- [ ] **Step 5: Update `selectAgent` to clear debug timeline**

In the `selectAgent` function, find `debugLog.innerHTML = '';` and replace with:

```javascript
            debugTimeline.innerHTML = '';
            resetSessionSummary();
```

(Note: `selectAgent` currently doesn't have this line — the debug panel clearing was done through `clearSession`. Verify by checking the `selectAgent` function body. If there's no debug clear there, skip this step.)

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/chat.html
git commit -m "feat: replace debug panel JS with observability state model and timeline rendering"
```

---

### Task 5: Integration test

**Files:**
- No file changes — manual testing

- [ ] **Step 1: Start the application**

Run: `mvn spring-boot:run`

- [ ] **Step 2: Open browser and verify**

Open `http://localhost:8080`. Verify:
1. Right panel shows "Runtime" header, summary card with zeros, empty timeline
2. Send a chat message — verify timeline shows LLM call rows with token counts and duration
3. Switch to a tool-calling agent, send a message — verify timeline shows tool rows with duration and status
4. Verify no thinking content appears in the debug panel
5. Verify thinking box still works in the chat area
6. Click CLEAR — verify timeline clears and summary resets to zero

- [ ] **Step 3: Final commit if any fixes needed**

```bash
git add -A
git commit -m "fix: debug panel observability integration fixes"
```

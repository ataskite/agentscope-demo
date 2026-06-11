# Distributed Session Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a session-demo agent with frontend session type switching to showcase AgentScope 2.0's `JsonSession` file-backed persistence vs default `InMemorySession`.

**Architecture:** Add `SessionConfig` to `AgentConfig`, add `createSession(String type)` to `AgentFactory`, thread `sessionType` through ChatController → AgentService → SessionManagerService to create the correct Session implementation. Frontend shows a session type selector when agent has `sessionConfig`.

**Tech Stack:** AgentScope 2.0.0-RC1 (`io.agentscope.core.session.JsonSession`), Spring Boot 3.5, vanilla JS frontend.

---

### Task 1: Add SessionConfig to AgentConfig

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentConfig.java`

- [ ] **Step 1: Add SessionConfig inner class and field**

Add after the `permissionConfig` field (line 83) in `AgentConfig.java`:

```java
    // === Session fields ===
    private SessionConfig sessionConfig;

    @Setter
    @Getter
    public static class SessionConfig {
        private String defaultType = "memory"; // memory or json
        private String storagePath;            // optional, defaults to ~/.agentscope/demo-sessions/
    }
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/AgentConfig.java
git commit -m "feat: add SessionConfig inner class and field to AgentConfig"
```

---

### Task 2: Add sessionType to ChatRequest

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/model/ChatRequest.java`

- [ ] **Step 1: Add sessionType field**

Add after the `permissionMode` field (around line 15) in `ChatRequest.java`:

```java
    private String sessionType;

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/skloda/agentscope/model/ChatRequest.java
git commit -m "feat: add sessionType field to ChatRequest"
```

---

### Task 3: Add createSession(type) to AgentFactory

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentFactory.java`

- [ ] **Step 1: Add import**

Add after the existing `InMemorySession` import (line 23):

```java
import io.agentscope.core.session.JsonSession;
```

- [ ] **Step 2: Add createSession overload**

Replace the existing `createSession()` method (line 69-71):

```java
    public Session createSession() {
        return new InMemorySession();
    }
```

with:

```java
    public Session createSession() {
        return new InMemorySession();
    }

    public Session createSession(String type, String storagePath) {
        if ("json".equalsIgnoreCase(type)) {
            Path dir = (storagePath != null && !storagePath.isBlank())
                    ? Path.of(storagePath)
                    : Path.of(System.getProperty("user.home"), ".agentscope", "demo-sessions");
            return new JsonSession(dir);
        }
        return new InMemorySession();
    }
```

Add `import java.nio.file.Path;` at top if not already present (check — it may already be there).

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/AgentFactory.java
git commit -m "feat: add createSession(type, storagePath) to AgentFactory for JsonSession support"
```

---

### Task 4: Thread sessionType through SessionManagerService → AgentService → ChatController

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/service/SessionManagerService.java`
- Modify: `src/main/java/com/skloda/agentscope/service/AgentService.java`
- Modify: `src/main/java/com/skloda/agentscope/controller/ChatController.java`

- [ ] **Step 1: SessionManagerService — add session-type-aware session creation**

In `SessionManagerService.java`, add a new overloaded `getOrCreateSession` that accepts `sessionType`:

```java
    public SessionContext getOrCreateSession(String sessionId, String agentId, String sessionType) {
        if (sessionId != null && !sessionId.isBlank()) {
            SessionContext cached = activeSessions.get(sessionId);
            if (cached != null) {
                cached.touch();
                return cached;
            }
        }
        return createNewSession(agentId, sessionType);
    }

    public SessionContext createNewSession(String agentId, String sessionType) {
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return createSessionContext(sessionId, agentId, sessionType);
    }

    private SessionContext createSessionContext(String sessionId, String agentId, String sessionType) {
        AgentConfig config = configService.getAgentConfig(agentId);
        String effectiveType = resolveSessionType(config, sessionType);
        String storagePath = config.getSessionConfig() != null ? config.getSessionConfig().getStoragePath() : null;

        Session session = agentFactory.createSession(effectiveType, storagePath);
        ReActAgent agent = agentFactory.createAgentForSession(agentId, session);

        SessionContext ctx = new SessionContext(sessionId, agentId, agent, session);
        activeSessions.put(sessionId, ctx);
        log.info("Created session: {} for agent: {} [type={}]", sessionId, agentId, effectiveType);
        return ctx;
    }

    private String resolveSessionType(AgentConfig config, String requestType) {
        if (requestType != null && !requestType.isBlank()) return requestType;
        if (config.getSessionConfig() != null) return config.getSessionConfig().getDefaultType();
        return "memory";
    }
```

The existing `createSessionContext(String sessionId, String agentId)` method (line 99) calls the old `agentFactory.createSession()` — update it to delegate:

```java
    private SessionContext createSessionContext(String sessionId, String agentId) {
        return createSessionContext(sessionId, agentId, (String) null);
    }
```

- [ ] **Step 2: AgentService — add sessionType to createStreamFlux**

Add an 11-param overload of `createStreamFlux` in `AgentService.java`. Update the existing 10-param method (the one with `permissionMode`) to delegate:

Change the existing 10-param method's body to:
```java
    public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                       String filePath, String fileName,
                                                       String sessionId,
                                                       List<ChatRequest.ImageFile> images,
                                                       ChatRequest.AudioFile audio,
                                                       String userId,
                                                       String executionMode,
                                                       String permissionMode) {
        return createStreamFlux(agentId, message, filePath, fileName, sessionId, images, audio, userId, executionMode, permissionMode, null);
    }
```

Add the 11-param method:
```java
    public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                       String filePath, String fileName,
                                                       String sessionId,
                                                       List<ChatRequest.ImageFile> images,
                                                       ChatRequest.AudioFile audio,
                                                       String userId,
                                                       String executionMode,
                                                       String permissionMode,
                                                       String sessionType) {
        Msg userMsg = buildUserMessage(message, filePath, fileName, images, audio);

        if (harnessAgentService != null) {
            AgentConfig cfg = runtimeFactory.getConfigService().findAgentConfig(agentId).orElse(null);
            if (cfg != null && cfg.getType() == AgentType.HARNESS) {
                return harnessAgentService.createStreamFlux(agentId, message, filePath, fileName, sessionId, userId, executionMode);
            }
        }

        String runId = workflowRunService.startRun(agentId, sessionId,
                buildInputPreview(message, filePath, fileName, images, audio));

        Flux<Map<String, Object>> stream;
        if (sessionId != null && !sessionId.isBlank()) {
            stream = createSessionStreamFlux(sessionId, agentId, userMsg, permissionMode, sessionType);
        } else {
            StreamingAgentRuntime runtime = (permissionMode != null && !permissionMode.isBlank())
                    ? runtimeFactory.createRuntime(agentId, permissionMode)
                    : runtimeFactory.createRuntime(agentId);
            stream = runtime.stream(userMsg);
        }

        return recordWorkflowRun(runId, recordChatTranscript(agentId,
                buildTranscriptUserText(message, fileName, images, audio), stream));
    }
```

Update `createSessionStreamFlux` to accept `sessionType`:
```java
    private Flux<Map<String, Object>> createSessionStreamFlux(String sessionId, String agentId,
                                                                Msg userMsg, String permissionMode,
                                                                String sessionType) {
        SessionManagerService.SessionContext ctx =
                sessionManagerService.getOrCreateSession(sessionId, agentId, sessionType);

        String effectiveSessionId = ctx.getSessionId();

        StreamingAgentRuntime runtime = (permissionMode != null && !permissionMode.isBlank())
                ? runtimeFactory.createRuntimeWithSession(agentId, ctx.getSession(), permissionMode)
                : runtimeFactory.createRuntimeWithSession(agentId, ctx.getSession());

        return runtime.stream(userMsg)
                .doFinally(signal -> {
                    sessionManagerService.saveSession(effectiveSessionId);
                    log.debug("Session {} saved after stream completion ({})", effectiveSessionId, signal);
                });
    }
```

- [ ] **Step 3: ChatController — forward sessionType**

In `ChatController.sendMessage` (line 136), add `request.getSessionType()` as the last argument:

```java
        return agentService.createStreamFlux(
                        request.getAgentId(), message,
                        request.getFilePath(), request.getFileName(),
                        sessionId,
                        request.getImages(),
                        request.getAudio(),
                        request.getUserId(),
                        request.getExecutionMode(),
                        request.getPermissionMode(),
                        request.getSessionType())
```

Also add `sessionConfig` to `toAgentConfigPreview` — add after `target.setPermissionConfig(source.getPermissionConfig())`:

```java
        target.setSessionConfig(source.getSessionConfig());
```

- [ ] **Step 4: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/service/SessionManagerService.java \
        src/main/java/com/skloda/agentscope/service/AgentService.java \
        src/main/java/com/skloda/agentscope/controller/ChatController.java
git commit -m "feat: thread sessionType through SessionManagerService → AgentService → ChatController"
```

---

### Task 5: Add session-demo agent to agents.yml

**Files:**
- Modify: `src/main/resources/config/agents.yml`

- [ ] **Step 1: Add session-demo agent config**

Append to the end of `agents.yml`:

```yaml
  # === 2.0 Distributed Session Demo ===
  - agentId: session-demo
    category: demo
    type: SINGLE
    name: 会话持久化助手
    description: 展示 AgentScope 2.0 会话持久化 — 内存/文件两种模式实时切换，重启后历史恢复
    modelName: qwen-plus
    streaming: true
    enableThinking: true
    systemPrompt: |
      你是一个会话持久化演示助手。用户可以切换 InMemory 和 JsonSession 模式来体验不同的会话持久化行为。
      在 JsonSession 模式下，对话历史会被持久化到文件，应用重启后可以恢复。
    userTools:
      - get_current_time
      - calculate_sum
      - web_search
    sessionConfig:
      defaultType: json
    samplePrompts:
      - prompt: "记住我的名字是小明"
        expectedBehavior: "JsonSession 模式下保存到文件，重启后可恢复"
      - prompt: "我叫什么名字？"
        expectedBehavior: "重启后 JsonSession 恢复历史，能记住用户名字"
      - prompt: "帮我搜索今天的天气"
        expectedBehavior: "正常工具调用，Session 类型不影响功能"
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/config/agents.yml
git commit -m "feat: add session-demo agent to agents.yml"
```

---

### Task 6: Frontend — session type selector UI

**Files:**
- Modify: `src/main/resources/static/scripts/state.js`
- Modify: `src/main/resources/static/scripts/modules/agents.js`
- Modify: `src/main/resources/static/scripts/chat.js`
- Modify: `src/main/resources/static/templates/chat.html`
- Modify: `src/main/resources/static/styles/modules/chat.css`

- [ ] **Step 1: Add sessionType to state.js**

Add `sessionType: null` to the state object (line 22, after `permissionMode: null`). Note: add a comma after `permissionMode: null`:

```javascript
    permissionMode: null,
    sessionType: null
```

Add getter/setter after the existing `permissionMode` defineWindowStateProperty:

```javascript
defineWindowStateProperty('sessionType', {
    get: function() { return state.sessionType; },
    set: function(val) { state.sessionType = val; }
});
```

- [ ] **Step 2: Add session type selector in agents.js**

Add after the existing `renderPermissionSelector` function:

```javascript
window.renderSessionTypeSelector = function(agentConfig) {
    var container = document.getElementById('sessionTypeContainer');
    if (!container) return;
    container.innerHTML = '';

    if (!agentConfig || !agentConfig.sessionConfig) {
        container.style.display = 'none';
        window.sessionType = null;
        return;
    }

    container.style.display = 'flex';
    var types = [
        { value: 'memory', label: 'InMemory (内存)', desc: '内存会话，重启丢失' },
        { value: 'json', label: 'JsonSession (持久)', desc: '文件持久化，重启恢复' }
    ];

    var defaultType = agentConfig.sessionConfig.defaultType || 'memory';

    types.forEach(function(t) {
        var btn = document.createElement('button');
        btn.className = 'session-type-btn' + (t.value === defaultType ? ' active' : '');
        btn.dataset.type = t.value;
        btn.title = t.desc;
        btn.textContent = t.label;
        btn.onclick = function() {
            container.querySelectorAll('.session-type-btn').forEach(function(b) { b.classList.remove('active'); });
            btn.classList.add('active');
            window.sessionType = t.value;
        };
        container.appendChild(btn);
    });

    window.sessionType = defaultType;
};
```

In the `selectAgent` function, add after the harness controls block (after line 177) and before `showSamplePrompts(agentId)`:

```javascript
    // Show/hide Session Type controls
    window.renderSessionTypeSelector(agents[agentId] ? agents[agentId].config : null);
```

- [ ] **Step 3: Add session type container to chat.html**

Add after the `permissionModeContainer` div:

```html
                    <div id="sessionTypeContainer" class="session-type-container" style="display:none;"></div>
```

- [ ] **Step 4: Add CSS for session type buttons**

Append to `chat.css`:

```css
/* ===== SESSION TYPE SELECTOR ===== */
.session-type-container {
    display: flex;
    gap: 6px;
    padding: 8px 16px;
    background: var(--bg-secondary, #1e1e2e);
    border-top: 1px solid var(--border, #333);
}
.session-type-btn {
    padding: 4px 12px;
    border: 1px solid var(--border, #444);
    border-radius: 4px;
    background: transparent;
    color: var(--text-muted, #888);
    font-size: 12px;
    cursor: pointer;
    transition: all 0.15s;
}
.session-type-btn.active {
    background: var(--accent-green, #00b894);
    color: #fff;
    border-color: var(--accent-green, #00b894);
}
.session-type-btn:hover:not(.active) {
    border-color: var(--accent-green, #00b894);
    color: var(--text, #ccc);
}
```

- [ ] **Step 5: Send sessionType in chat request**

In `chat.js`, find where the request body is built and add after `permissionMode`:

```javascript
            sessionType: window.sessionType || null
```

- [ ] **Step 6: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/scripts/state.js \
        src/main/resources/static/scripts/modules/agents.js \
        src/main/resources/static/scripts/chat.js \
        src/main/resources/templates/chat.html \
        src/main/resources/static/styles/modules/chat.css
git commit -m "feat: add session type selector UI and wire to chat request"
```

---

### Task 7: End-to-end verification

**Files:**
- No new files

- [ ] **Step 1: Run full test suite**

Run: `mvn test -pl . -Dtest='!ChatControllerStreamTest' 2>&1 | tail -10`
Expected: All tests pass

- [ ] **Step 2: Verify app starts**

Run: `mvn spring-boot:run -Dspring-boot.run.arguments="--agentscope.model.dashscope.api-key=${DASHSCOPE_API_KEY}" &`
Then: `curl -s http://localhost:8080/api/agents | python3 -m json.tool | grep -A5 session-demo`
Expected: session-demo agent appears in agent list with `sessionConfig` field

- [ ] **Step 3: Final commit if any fixes needed**

```bash
git add -A && git commit -m "fix: session demo e2e adjustments"
```

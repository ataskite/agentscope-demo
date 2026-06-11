# Permission Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a permission-demo agent with frontend mode switching to showcase AgentScope 2.0 Permission system.

**Architecture:** Add `PermissionConfig` to `AgentConfig`, create `PermissionContextFactory` to build `PermissionContextState` from mode string + config, thread `permissionMode` through ChatController → AgentService → AgentRuntimeFactory → AgentFactory to inject into `ReActAgent.Builder`. Frontend shows a mode dropdown when agent has `permissionConfig`.

**Tech Stack:** AgentScope 2.0.0-RC1 (`io.agentscope.core.permission.*`), Spring Boot 3.5, vanilla JS frontend.

---

### Task 1: Add PermissionConfig to AgentConfig

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentConfig.java`

- [ ] **Step 1: Add PermissionConfig inner class and field**

Add after the `middlewares` field (line 80) in `AgentConfig.java`:

```java
    // === Permission fields ===
    private PermissionConfig permissionConfig;

    @Setter
    @Getter
    public static class PermissionConfig {
        private String defaultMode = "bypass";
        private List<String> denyTools = new ArrayList<>();
        private List<String> askTools = new ArrayList<>();
    }
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/AgentConfig.java
git commit -m "feat: add PermissionConfig inner class and field to AgentConfig"
```

---

### Task 2: Create PermissionContextFactory

**Files:**
- Create: `src/main/java/com/skloda/agentscope/permission/PermissionContextFactory.java`
- Create: `src/test/java/com/skloda/agentscope/permission/PermissionContextFactoryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.skloda.agentscope.permission;

import com.skloda.agentscope.agent.AgentConfig;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PermissionContextFactoryTest {

    private final PermissionContextFactory factory = new PermissionContextFactory();

    @Test
    void bypassModeAllowsAll() {
        PermissionContextState ctx = factory.build("bypass", null);
        assertEquals(PermissionMode.BYPASS, ctx.getMode());
        assertTrue(ctx.isTrivial() || ctx.getMode() == PermissionMode.BYPASS);
    }

    @Test
    void exploreModeSetsExplore() {
        PermissionContextState ctx = factory.build("explore", null);
        assertEquals(PermissionMode.EXPLORE, ctx.getMode());
    }

    @Test
    void acceptEditsModeWithAskRules() {
        AgentConfig.PermissionConfig config = new AgentConfig.PermissionConfig();
        config.setAskTools(java.util.List.of("execute_shell_command"));

        PermissionContextState ctx = factory.build("accept_edits", config);
        assertEquals(PermissionMode.ACCEPT_EDITS, ctx.getMode());
        assertFalse(ctx.getAskRules().isEmpty());
        assertTrue(ctx.getAskRules().containsKey("execute_shell_command"));
    }

    @Test
    void denyToolsAppliedRegardlessOfMode() {
        AgentConfig.PermissionConfig config = new AgentConfig.PermissionConfig();
        config.setDenyTools(java.util.List.of("write_text_file"));

        PermissionContextState ctx = factory.build("bypass", config);
        assertEquals(PermissionMode.BYPASS, ctx.getMode());
        assertFalse(ctx.getDenyRules().isEmpty());
        assertTrue(ctx.getDenyRules().containsKey("write_text_file"));
    }

    @Test
    void nullConfigUsesModeOnly() {
        PermissionContextState ctx = factory.build("explore", null);
        assertEquals(PermissionMode.EXPLORE, ctx.getMode());
        assertTrue(ctx.getAllowRules().isEmpty());
        assertTrue(ctx.getDenyRules().isEmpty());
        assertTrue(ctx.getAskRules().isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=PermissionContextFactoryTest -pl . 2>&1 | tail -5`
Expected: FAIL (class not found)

- [ ] **Step 3: Write implementation**

```java
package com.skloda.agentscope.permission;

import com.skloda.agentscope.agent.AgentConfig;
import io.agentscope.core.permission.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PermissionContextFactory {

    public PermissionContextState build(String mode, AgentConfig.PermissionConfig config) {
        PermissionContextState.Builder builder = PermissionContextState.builder()
                .mode(PermissionMode.fromString(mode != null ? mode : "bypass"));

        if (config != null) {
            applyDenyRules(builder, config.getDenyTools());
            if ("accept_edits".equals(mode)) {
                applyAskRules(builder, config.getAskTools());
            }
        }

        return builder.build();
    }

    private void applyDenyRules(PermissionContextState.Builder builder, List<String> denyTools) {
        if (denyTools == null) return;
        for (String tool : denyTools) {
            builder.addDenyRule(tool, new PermissionRule(tool, null, PermissionBehavior.DENY, "config"));
        }
    }

    private void applyAskRules(PermissionContextState.Builder builder, List<String> askTools) {
        if (askTools == null) return;
        for (String tool : askTools) {
            builder.addAskRule(tool, new PermissionRule(tool, null, PermissionBehavior.ASK, "config"));
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=PermissionContextFactoryTest -pl . 2>&1 | tail -5`
Expected: Tests run: 5, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/permission/PermissionContextFactory.java src/test/java/com/skloda/agentscope/permission/PermissionContextFactoryTest.java
git commit -m "feat: add PermissionContextFactory for building PermissionContextState"
```

---

### Task 3: Add permissionMode to ChatRequest

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/model/ChatRequest.java`

- [ ] **Step 1: Add permissionMode field**

Add after the `executionMode` field (line 14) in `ChatRequest.java`:

```java
    private String permissionMode;

    public String getPermissionMode() {
        return permissionMode;
    }

    public void setPermissionMode(String permissionMode) {
        this.permissionMode = permissionMode;
    }
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/skloda/agentscope/model/ChatRequest.java
git commit -m "feat: add permissionMode field to ChatRequest"
```

---

### Task 4: Integrate permission into AgentFactory

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentFactory.java`

- [ ] **Step 1: Add PermissionContextFactory dependency and inject PermissionContextState into agent builder**

Add import at top:
```java
import com.skloda.agentscope.permission.PermissionContextFactory;
import io.agentscope.core.permission.PermissionContextState;
```

Add field after `middlewareRegistry`:
```java
    private final PermissionContextFactory permissionContextFactory;
```

Update constructor to add `PermissionContextFactory permissionContextFactory` parameter and assign it.

Add a new overloaded `buildAgent` method after the existing one (line 82):
```java
    private ReActAgent buildAgent(String agentId, Session session, String permissionMode, Hook... hooks) {
        ReActAgent agent = buildAgent(agentId, session, hooks);
        return agent;
    }
```

Wait — the PermissionContextState must be set on the Builder BEFORE build(). So we need to modify the existing `buildAgent` method instead. Add the permission injection in the existing `buildAgent` method, after the middlewares block (around line 165) and before `builder.enablePendingToolRecovery(true)`:

```java
        // Configure permission context if applicable
        String effectiveMode = resolvePermissionMode(config, permissionMode);
        if (effectiveMode != null) {
            PermissionContextState permContext = permissionContextFactory.build(
                    effectiveMode, config.getPermissionConfig());
            builder.permissionContext(permContext);
            log.info("  Configured permission mode: {} for agent: {}", effectiveMode, agentId);
        }
```

Add the helper method:
```java
    private String resolvePermissionMode(AgentConfig config, String requestMode) {
        if (config.getPermissionConfig() == null) return null;
        if (requestMode != null && !requestMode.isBlank()) return requestMode;
        return config.getPermissionConfig().getDefaultMode();
    }
```

Also add overloaded public methods that accept `permissionMode`:
```java
    public ReActAgent createAgentForSession(String agentId, Session session, String permissionMode, Hook... hooks) {
        return buildAgentWithPermission(agentId, session, permissionMode, hooks);
    }

    public ReActAgent createAgent(String agentId, String permissionMode, Hook... hooks) {
        return buildAgentWithPermission(agentId, new InMemorySession(), permissionMode, hooks);
    }

    private ReActAgent buildAgentWithPermission(String agentId, Session session, String permissionMode, Hook... hooks) {
        AgentConfig config = configService.getAgentConfig(agentId);
        log.info("Creating agent: {} ({}) [permissionMode={}]", config.getName(), agentId, permissionMode);

        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.getModelName())
                .stream(config.isStreaming())
                .enableThinking(config.isEnableThinking())
                .formatter(new DashScopeChatFormatter())
                .build();

        ReActAgent.Builder builder = ReActAgent.builder()
                .name(config.getName())
                .sysPrompt(config.getSystemPrompt())
                .model(model)
                .session(session)
                .sessionKey(SimpleSessionKey.of(agentId));

        if (config.isPlanEnabled()) {
            builder.enablePlan();
        }

        Toolkit toolkit = new Toolkit();
        registerToolsAndSkills(builder, toolkit, config, agentId);
        registerMcpTools(config, toolkit);

        if (config.getStructuredOutputClass() != null && !config.getStructuredOutputClass().isBlank()) {
            StructuredOutputReminder reminder = "PROMPT".equalsIgnoreCase(config.getStructuredOutputReminder())
                    ? StructuredOutputReminder.PROMPT
                    : StructuredOutputReminder.TOOL_CHOICE;
            builder.structuredOutputReminder(reminder);
        }

        if (config.isRagEnabled()) {
            RAGMode ragMode = parseRagMode(config.getRagMode());
            builder.knowledge(knowledgeService.getKnowledge())
                    .ragMode(ragMode)
                    .retrieveConfig(RetrieveConfig.builder()
                            .limit(config.getRagRetrieveLimit())
                            .scoreThreshold(config.getRagScoreThreshold())
                            .build());
        }

        if (config.getLongTermMemory() != null && !"none".equals(config.getLongTermMemory().getType())) {
            LongTermMemory ltm = createLongTermMemory(config.getLongTermMemory());
            if (ltm != null) {
                LongTermMemoryMode mode = parseLtmMode(config.getLongTermMemory().getMode());
                builder.longTermMemory(ltm).longTermMemoryMode(mode);
            }
        }

        if (hooks != null && hooks.length > 0) {
            builder.hooks(List.of(hooks));
        }

        if (config.getMiddlewares() != null && !config.getMiddlewares().isEmpty()) {
            List<MiddlewareBase> middlewares = config.getMiddlewares().stream()
                    .map(middlewareRegistry::create)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!middlewares.isEmpty()) {
                builder.middlewares(middlewares);
            }
        }

        // Configure permission context
        String effectiveMode = resolvePermissionMode(config, permissionMode);
        if (effectiveMode != null) {
            PermissionContextState permContext = permissionContextFactory.build(
                    effectiveMode, config.getPermissionConfig());
            builder.permissionContext(permContext);
            log.info("  Configured permission mode: {} for agent: {}", effectiveMode, agentId);
        }

        builder.enablePendingToolRecovery(true);
        return builder.build();
    }

    private String resolvePermissionMode(AgentConfig config, String requestMode) {
        if (config.getPermissionConfig() == null) return null;
        if (requestMode != null && !requestMode.isBlank()) return requestMode;
        return config.getPermissionConfig().getDefaultMode();
    }
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/AgentFactory.java
git commit -m "feat: integrate PermissionContextFactory into AgentFactory"
```

---

### Task 5: Thread permissionMode through the call chain

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java`
- Modify: `src/main/java/com/skloda/agentscope/runtime/AgentRuntimeFactory.java`
- Modify: `src/main/java/com/skloda/agentscope/service/AgentService.java`
- Modify: `src/main/java/com/skloda/agentscope/controller/ChatController.java`

This is mechanical plumbing. Add `permissionMode` parameter through each layer.

- [ ] **Step 1: CompositeAgentFactory — add overloaded methods**

Add these methods to `CompositeAgentFactory.java`:

```java
    public ReActAgent createSingleAgent(String agentId, String permissionMode, Hook... hooks) {
        return agentFactory.createAgent(agentId, permissionMode, hooks);
    }

    public ReActAgent createSingleAgentForSession(String agentId, Session session, String permissionMode, Hook... hooks) {
        return agentFactory.createAgentForSession(agentId, session, permissionMode, hooks);
    }
```

Requires imports: `import io.agentscope.core.session.Session;` and `import io.agentscope.core.hook.Hook;` (likely already present).

- [ ] **Step 2: AgentRuntimeFactory — add overloaded create methods**

Add to `AgentRuntimeFactory.java`:

```java
    public StreamingAgentRuntime createRuntime(String agentId, String permissionMode) {
        log.debug("Creating AgentRuntime for agent: {} [permissionMode={}]", agentId, permissionMode);
        AgentConfig config = configService.getAgentConfig(agentId);
        AgentType type = config.getType() != null ? config.getType() : AgentType.SINGLE;
        return switch (type) {
            case SINGLE -> createSingleRuntimeWithPermission(agentId, permissionMode);
            case ROUTING, HANDOFFS, STATE_GRAPH, HARNESS -> createRuntime(agentId);
            case SEQUENTIAL, PARALLEL, DEBATE, LOOP, MSG_HUB, SUBAGENT_SEQ, SUBAGENT_PAR ->
                throw new UnsupportedOperationException("Pattern " + type + " is disabled for 2.0 migration");
        };
    }

    public StreamingAgentRuntime createRuntimeWithSession(String agentId, Session session, String permissionMode) {
        log.debug("Creating AgentRuntime with session for agent: {} [permissionMode={}]", agentId, permissionMode);
        AgentConfig config = configService.getAgentConfig(agentId);
        AgentType type = config.getType() != null ? config.getType() : AgentType.SINGLE;
        return switch (type) {
            case SINGLE -> createSingleRuntimeWithSessionAndPermission(agentId, session, permissionMode);
            case ROUTING, HANDOFFS, STATE_GRAPH, HARNESS -> createRuntimeWithSession(agentId, session);
            case SEQUENTIAL, PARALLEL, DEBATE, LOOP, MSG_HUB, SUBAGENT_SEQ, SUBAGENT_PAR ->
                throw new UnsupportedOperationException("Pattern " + type + " is disabled for 2.0 migration");
        };
    }

    private StreamingAgentRuntime createSingleRuntimeWithPermission(String agentId, String permissionMode) {
        ObservabilityHook hook = new ObservabilityHook();
        AgentConfig config = configService.getAgentConfig(agentId);
        ApprovalHook approvalHook = createApprovalHookIfNeeded(config);
        ReActAgent agent = approvalHook != null
                ? compositeFactory.createSingleAgent(agentId, permissionMode, hook, approvalHook)
                : compositeFactory.createSingleAgent(agentId, permissionMode, hook);
        if (hasStructuredOutput(config)) {
            return new StructuredOutputAgentRuntime(agent, hook, config.getStructuredOutputClass());
        }
        return new AgentRuntime(agent, hook, approvalHook, approvalService, agentId);
    }

    private StreamingAgentRuntime createSingleRuntimeWithSessionAndPermission(String agentId, Session session, String permissionMode) {
        ObservabilityHook hook = new ObservabilityHook();
        AgentConfig config = configService.getAgentConfig(agentId);
        ApprovalHook approvalHook = createApprovalHookIfNeeded(config);
        ReActAgent agent = approvalHook != null
                ? compositeFactory.createSingleAgentForSession(agentId, session, permissionMode, hook, approvalHook)
                : compositeFactory.createSingleAgentForSession(agentId, session, permissionMode, hook);
        if (hasStructuredOutput(config)) {
            return new StructuredOutputAgentRuntime(agent, hook, config.getStructuredOutputClass());
        }
        return new AgentRuntime(agent, hook, approvalHook, approvalService, agentId);
    }
```

- [ ] **Step 3: AgentService — add permissionMode to createStreamFlux**

Add a new overloaded `createStreamFlux` in `AgentService.java`. The existing chain of overloads ends at line 99-131 with 8 params. Add the permissionMode variant:

```java
    public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                       String filePath, String fileName,
                                                       String sessionId,
                                                       List<ChatRequest.ImageFile> images,
                                                       ChatRequest.AudioFile audio,
                                                       String userId,
                                                       String executionMode,
                                                       String permissionMode) {
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
            SessionManagerService.SessionContext ctx =
                    sessionManagerService.getOrCreateSession(sessionId, agentId);
            StreamingAgentRuntime runtime = runtimeFactory.createRuntimeWithSession(agentId, ctx.getSession(), permissionMode);
            stream = runtime.stream(userMsg)
                    .doFinally(signal -> sessionManagerService.saveSession(ctx.getSessionId()));
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

Update the existing 8-param overload to delegate:
```java
    // Change existing 8-param overload (line 99-131) to:
    public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                       String filePath, String fileName,
                                                       String sessionId,
                                                       List<ChatRequest.ImageFile> images,
                                                       ChatRequest.AudioFile audio,
                                                       String userId,
                                                       String executionMode) {
        return createStreamFlux(agentId, message, filePath, fileName, sessionId,
                images, audio, userId, executionMode, null);
    }
```

- [ ] **Step 4: ChatController — pass permissionMode from request**

In `ChatController.sendMessage` (line 136), add `request.getPermissionMode()` as the last argument:

```java
        return agentService.createStreamFlux(
                        request.getAgentId(), message,
                        request.getFilePath(), request.getFileName(),
                        sessionId,
                        request.getImages(),
                        request.getAudio(),
                        request.getUserId(),
                        request.getExecutionMode(),
                        request.getPermissionMode())
```

- [ ] **Step 5: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java \
        src/main/java/com/skloda/agentscope/runtime/AgentRuntimeFactory.java \
        src/main/java/com/skloda/agentscope/service/AgentService.java \
        src/main/java/com/skloda/agentscope/controller/ChatController.java
git commit -m "feat: thread permissionMode through ChatController → AgentService → AgentFactory"
```

---

### Task 6: Expose permissionConfig in agent API response

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/controller/ChatController.java`

- [ ] **Step 1: Add permissionConfig to toAgentConfigPreview**

In `ChatController.toAgentConfigPreview` (around line 332), add after `target.setMiddlewares(...)`:

```java
        target.setPermissionConfig(source.getPermissionConfig());
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/skloda/agentscope/controller/ChatController.java
git commit -m "feat: expose permissionConfig in agent API response"
```

---

### Task 7: Add permission-demo agent to agents.yml

**Files:**
- Modify: `src/main/resources/config/agents.yml`

- [ ] **Step 1: Add permission-demo agent config**

Add at the end of the agents list (before any harness agents), following the same format:

```yaml
  - agentId: permission-demo
    category: demo
    type: SINGLE
    name: 权限沙箱助手
    description: 展示 AgentScope 2.0 工具权限控制 — 只读/编辑/无限制三种模式实时切换
    modelName: qwen-plus
    streaming: true
    enableThinking: true
    systemPrompt: |
      你是一个权限沙箱演示助手。你拥有多种工具，但不同权限模式下能使用的工具不同。
      当工具被拒绝时，请向用户解释当前模式下的限制，并建议切换到更宽松的模式。
      当前你处于 {mode} 模式，请根据模式说明你的权限范围。
    userTools:
      - get_current_time
      - calculate_sum
      - web_search
      - parse_docx
    systemTools:
      - write_text_file
      - edit_docx
      - execute_shell_command
    permissionConfig:
      defaultMode: explore
      askTools:
        - execute_shell_command
    samplePrompts:
      - prompt: "现在几点了？"
        expectedBehavior: "EXPLORE 模式下允许调用 get_current_time"
      - prompt: "帮我写一个文件到 /tmp/test.txt，内容是 hello world"
        expectedBehavior: "EXPLORE 模式下 write_text_file 被拒绝，提示切换模式"
      - prompt: "执行 ls -la /tmp 命令"
        expectedBehavior: "ACCEPT_EDITS 模式下 execute_shell_command 需要审批"
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/config/agents.yml
git commit -m "feat: add permission-demo agent to agents.yml"
```

---

### Task 8: Frontend — permission mode selector UI

**Files:**
- Modify: `src/main/resources/static/scripts/state.js`
- Modify: `src/main/resources/static/scripts/modules/agents.js`
- Modify: `src/main/resources/static/scripts/chat.js`

- [ ] **Step 1: Add permissionMode to state.js**

Add after `harnessMode: null` in the state object (line 21):

```javascript
    permissionMode: null
```

Add getter/setter in state.js after the existing defineWindowStateProperty calls:

```javascript
let permissionMode = state.permissionMode;

defineWindowStateProperty('permissionMode', {
    get: function() { return permissionMode; },
    set: function(val) { permissionMode = val; state.permissionMode = val; }
});
```

Export `permissionMode` via the existing module pattern.

- [ ] **Step 2: Add permission mode selector rendering in agents.js**

In the agent selection handler (where agents are selected and the chat UI updates), add logic to show/hide the permission dropdown. Find where `window.selectAgent` or similar is defined and add:

```javascript
// After agent is selected, check for permissionConfig
window.renderPermissionSelector = function(agentConfig) {
    var container = document.getElementById('permissionModeContainer');
    if (!container) return;
    container.innerHTML = '';

    if (!agentConfig || !agentConfig.permissionConfig) {
        container.style.display = 'none';
        window.permissionMode = null;
        return;
    }

    container.style.display = 'flex';
    var modes = [
        { value: 'explore', label: 'EXPLORE (只读模式)', desc: '只允许读取操作' },
        { value: 'accept_edits', label: 'ACCEPT_EDITS (编辑模式)', desc: '允许读写，危险操作需审批' },
        { value: 'bypass', label: 'BYPASS (无限制)', desc: '所有工具均可使用' }
    ];

    var defaultMode = agentConfig.permissionConfig.defaultMode || 'explore';

    modes.forEach(function(m) {
        var btn = document.createElement('button');
        btn.className = 'perm-mode-btn' + (m.value === defaultMode ? ' active' : '');
        btn.dataset.mode = m.value;
        btn.title = m.desc;
        btn.textContent = m.label;
        btn.onclick = function() {
            container.querySelectorAll('.perm-mode-btn').forEach(function(b) { b.classList.remove('active'); });
            btn.classList.add('active');
            window.permissionMode = m.value;
        };
        container.appendChild(btn);
    });

    window.permissionMode = defaultMode;
};
```

- [ ] **Step 3: Add permission mode container to chat.html**

Add in `chat.html` before the chat input area:

```html
<div id="permissionModeContainer" class="permission-mode-container" style="display:none;"></div>
```

- [ ] **Step 4: Add CSS for permission mode buttons**

Add to the appropriate CSS file:

```css
.permission-mode-container {
    display: flex;
    gap: 6px;
    padding: 8px 16px;
    background: var(--bg-secondary, #1e1e2e);
    border-top: 1px solid var(--border, #333);
}
.perm-mode-btn {
    padding: 4px 12px;
    border: 1px solid var(--border, #444);
    border-radius: 4px;
    background: transparent;
    color: var(--text-muted, #888);
    font-size: 12px;
    cursor: pointer;
    transition: all 0.15s;
}
.perm-mode-btn.active {
    background: var(--accent, #6c5ce7);
    color: #fff;
    border-color: var(--accent, #6c5ce7);
}
.perm-mode-btn:hover:not(.active) {
    border-color: var(--accent, #6c5ce7);
    color: var(--text, #ccc);
}
```

- [ ] **Step 5: Call renderPermissionSelector when agent is selected**

In the agent selection handler in `agents.js`, after setting the current agent, call:

```javascript
window.renderPermissionSelector(agent.config);
```

- [ ] **Step 6: Send permissionMode in chat request**

In `chat.js`, find where the `ChatRequest` body is built for `/chat/send` and add:

```javascript
    // Add permissionMode if set
    if (window.permissionMode) {
        body.permissionMode = window.permissionMode;
    }
```

- [ ] **Step 7: Verify with mvn compile and manual browser check**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/static/scripts/state.js \
        src/main/resources/static/scripts/modules/agents.js \
        src/main/resources/static/scripts/chat.js \
        src/main/resources/static/styles/ \
        src/main/resources/templates/chat.html
git commit -m "feat: add permission mode selector UI and wire to chat request"
```

---

### Task 9: End-to-end verification

**Files:**
- No new files

- [ ] **Step 1: Run full test suite**

Run: `mvn test -pl . 2>&1 | tail -10`
Expected: All tests pass

- [ ] **Step 2: Verify app starts**

Run: `mvn spring-boot:run -Dspring-boot.run.arguments="--agentscope.model.dashscope.api-key=${DASHSCOPE_API_KEY}" &`
Then: `curl -s http://localhost:8080/api/agents | python3 -m json.tool | grep -A3 permission-demo`
Expected: permission-demo agent appears in agent list with `permissionConfig` field

- [ ] **Step 3: Final commit if any fixes needed**

```bash
git add -A && git commit -m "fix: permission demo e2e adjustments"
```

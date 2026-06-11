# AgentScope 2.0 Permission Demo Design

## Goal

Create a dedicated demo agent showcasing the AgentScope 2.0 Permission system — a rule-based tool access control engine with multiple permission modes, demonstrating real-time mode switching from the frontend.

## Permission API Overview

`PermissionContextState` is the core configuration object:

- **Mode**: `DEFAULT` | `BYPASS` | `EXPLORE` | `ACCEPT_EDITS` | `DONT_ASK`
- **Rule tables**: `allowRules`, `denyRules`, `askRules` — keyed by tool name, each containing ordered `PermissionRule` entries
- **Working directories**: optional paths for file tool access scoping

`PermissionEngine` evaluates tool requests in priority order:
1. Deny rules (highest priority)
2. Ask rules
3. Tool self-check (bypass-immune)
4. Allow rules
5. BYPASS mode fallback
6. Default ASK (DENY under DONT_ASK)

Registration: `ReActAgent.builder().permissionContext(PermissionContextState)`.

## Demo Scenarios

Three permission modes, each showing different tool access behavior:

| Tool | Type | EXPLORE | ACCEPT_EDITS | BYPASS |
|------|------|---------|-------------|--------|
| `get_current_time` | Read-only | Allowed | Allowed | Allowed |
| `calculate_sum` | Read-only | Allowed | Allowed | Allowed |
| `web_search` | Read-only | Allowed | Allowed | Allowed |
| `parse_docx` | Read-only | Allowed | Allowed | Allowed |
| `write_text_file` | Write | Denied | Allowed | Allowed |
| `edit_docx` | Write | Denied | Allowed | Allowed |
| `execute_shell_command` | Dangerous | Denied | Ask | Allowed |

## Frontend: Mode Switching

When an agent has `permissionConfig`, a permission mode dropdown appears above the chat input area. Options:

- **EXPLORE (只读模式)** — Only read operations allowed
- **ACCEPT_EDITS (编辑模式)** — Read + write allowed, dangerous tools need approval
- **BYPASS (无限制)** — All tools allowed

Switching sends `permissionMode` in `ChatRequest`, backend rebuilds the agent with the new `PermissionContextState`.

## Backend Components

### AgentConfig

Add `permissionConfig` field:

```java
private PermissionConfig permissionConfig;

@Setter
@Getter
public static class PermissionConfig {
    private String defaultMode = "bypass";
    private List<String> denyTools = new ArrayList<>();
    private List<String> askTools = new ArrayList<>();
}
```

### ChatRequest

Add `permissionMode` field (optional, overrides agent default):

```java
private String permissionMode;
```

### PermissionContextFactory

New class `com.skloda.agentscope.permission.PermissionContextFactory`:

Builds `PermissionContextState` from mode string + `PermissionConfig`:

- `EXPLORE`: `PermissionMode.EXPLORE` — framework auto-allows read-only tools, denies writes
- `ACCEPT_EDITS`: `PermissionMode.ACCEPT_EDITS` + ask rules for tools in `askTools` list
- `BYPASS`: `PermissionMode.BYPASS` — all tools allowed
- Also applies `denyTools` from config as explicit deny rules regardless of mode

```java
@Component
public class PermissionContextFactory {
    public PermissionContextState build(String mode, PermissionConfig config) {
        var builder = PermissionContextState.builder()
                .mode(PermissionMode.fromString(mode));

        // Always apply explicit deny rules
        if (config != null && config.getDenyTools() != null) {
            for (String tool : config.getDenyTools()) {
                builder.addDenyRule(tool, PermissionRule.deny(tool, "config", "Denied by agent config"));
            }
        }

        // ACCEPT_EDITS mode: add ask rules for dangerous tools
        if ("accept_edits".equals(mode) && config != null && config.getAskTools() != null) {
            for (String tool : config.getAskTools()) {
                builder.addAskRule(tool, PermissionRule.ask(tool, "config", "Requires approval"));
            }
        }

        return builder.build();
    }
}
```

### AgentFactory Integration

In `buildAgent()`, after creating the ReActAgent.Builder:

1. Determine effective mode: request-level `permissionMode` > agent config `defaultMode`
2. Call `PermissionContextFactory.build(mode, config.getPermissionConfig())`
3. Call `builder.permissionContext(context)`

### AgentService / ChatController

`ChatController.sendMessage` passes `permissionMode` from request to `AgentService.streamEvents`. When mode differs from cached agent's current mode, rebuild agent with new `PermissionContextState`.

## Debug Panel

Permission decisions flow through existing ObservabilityHook event pipeline:
- Denied tool calls appear as error events in the timeline
- Approved tool calls show normally in tool_start/tool_end events
- No new frontend work needed for basic visibility

## Configuration

Add to `agents.yml`:

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
    - prompt: "帮我写一个文件到 /tmp/test.txt"
      expectedBehavior: "EXPLORE 模式下 write_text_file 被拒绝，提示切换模式"
    - prompt: "执行 ls -la 命令"
      expectedBehavior: "ACCEPT_EDITS 模式下 execute_shell_command 需要审批"
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `permission/PermissionContextFactory.java` | New | Build PermissionContextState from mode + config |
| `agent/AgentConfig.java` | Modify | Add PermissionConfig inner class and field |
| `agent/AgentFactory.java` | Modify | Inject PermissionContextState into agent builder |
| `model/ChatRequest.java` | Modify | Add permissionMode field |
| `service/AgentService.java` | Modify | Pass permissionMode, rebuild agent on mode change |
| `controller/ChatController.java` | Modify | Forward permissionMode from request |
| `config/agents.yml` | Modify | Add permission-demo agent config |
| `static/scripts/modules/agents.js` | Modify | Show permission mode selector when configured |
| `static/scripts/chat.js` | Modify | Send permissionMode in chat request |
| `static/scripts/state.js` | Modify | Track current permission mode |

## Out of Scope

- HITL approval flow for ASK mode (frontend approval dialog) — deferred to separate feature
- Working directory scoping — not needed for demo
- DONT_ASK / DEFAULT modes — only EXPLORE, ACCEPT_EDITS, BYPASS are demoed
- Persisting permission mode across sessions

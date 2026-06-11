# AgentScope 2.0 Distributed Session Demo Design

## Goal

Create a frontend-switchable session type demo showcasing AgentScope 2.0's `JsonSession` — a file-backed persistent session that survives application restarts, contrasted with the default `InMemorySession`.

## Session API Overview

AgentScope 2.0 provides two `Session` implementations:

- **`InMemorySession`** — in-memory HashMap, no-arg constructor, data lost on restart
- **`JsonSession`** — JSON file persistence, accepts `Path` for storage directory, data survives restart

Both implement the `Session` interface with `save()`, `get()`, `getList()`, `exists()`, `delete()`, `listSessionKeys()`.

`JsonSession` key differences: constructor takes `java.nio.file.Path`, `clearAllSessions()` returns `Mono<Integer>`, exposes `getSessionDirectory()`.

## Demo Scenarios

Users can switch between two session types at runtime:

| Scenario | Session Type | Behavior |
|----------|-------------|----------|
| Default chat | InMemory | Fast, ephemeral, lost on restart |
| Persistent chat | JsonSession | Survives restart, history preserved |

**User flow:**
1. Select JsonSession mode in the UI
2. Send several messages to build conversation history
3. Restart the Spring Boot application
4. Open the same session — history is restored
5. Switch to InMemory mode to contrast ephemeral behavior

## Frontend: Session Type Switch

When an agent has `sessionConfig` in its configuration, a session type selector appears above the chat input area. Options:

- **InMemory (内存模式)** — Fast, ephemeral, lost on restart
- **JsonSession (持久模式)** — File-backed, survives restart

Switching takes effect on the next message sent. The selector follows the same visual pattern as the Permission mode selector.

## Backend Components

### AgentConfig

Add `sessionConfig` field:

```java
private SessionConfig sessionConfig;

@Setter
@Getter
public static class SessionConfig {
    private String defaultType = "memory"; // memory or json
    private String storagePath;            // optional, defaults to ~/.agentscope/demo-sessions/
}
```

### ChatRequest

Add `sessionType` field (optional, overrides agent default):

```java
private String sessionType;
```

### AgentFactory

Add `createSession(String type)` method:

- `"memory"` → `new InMemorySession()`
- `"json"` → `new JsonSession(Path.of(storagePath))`
- Also add overloaded `buildAgentWithSessionType()` that creates the appropriate Session before building the agent

### AgentService

Add `sessionType` parameter to `createStreamFlux` chain. When creating a session, use the resolved session type to instantiate the correct `Session` implementation.

### ChatController

Forward `sessionType` from request. Also add `sessionConfig` to `toAgentConfigPreview` so the frontend knows to show the selector.

## Session Storage Path

- InMemory: no file path (in-memory only)
- JsonSession: `${user.home}/.agentscope/demo-sessions/<sessionId>.json`
- Configured via `SessionConfig.storagePath` or application default

## Configuration

Add to `agents.yml`:

```yaml
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
    storagePath: ${user.home}/.agentscope/demo-sessions/
  samplePrompts:
    - prompt: "记住我的名字是小明"
      expectedBehavior: "JsonSession 模式下保存到文件，重启后可恢复"
    - prompt: "我叫什么名字？"
      expectedBehavior: "重启后 JsonSession 恢复历史，能记住用户名字"
    - prompt: "帮我搜索今天的天气"
      expectedBehavior: "正常工具调用，Session 类型不影响功能"
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `agent/AgentConfig.java` | Modify | Add SessionConfig inner class and field |
| `model/ChatRequest.java` | Modify | Add sessionType field |
| `agent/AgentFactory.java` | Modify | Add createSession(type) method |
| `service/AgentService.java` | Modify | Add sessionType to createStreamFlux, resolve Session type |
| `controller/ChatController.java` | Modify | Forward sessionType, expose sessionConfig in API |
| `config/agents.yml` | Modify | Add session-demo agent config |
| `static/scripts/state.js` | Modify | Track current session type |
| `static/scripts/modules/agents.js` | Modify | Show session type selector when configured |
| `static/scripts/chat.js` | Modify | Send sessionType in chat request |
| `static/templates/chat.html` | Modify | Add session type container |
| `static/styles/modules/chat.css` | Modify | Add session type selector styles |

## Out of Scope

- Session migration (converting InMemory to JsonSession mid-conversation)
- Session backup/restore UI
- Multiple storage backends (database, Redis, etc.)
- Session encryption or compression
- Reactive `clearAllSessions()` endpoint

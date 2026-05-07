# P3: Planning & Memory Design

**Date**: 2026-05-07
**Scope**: PlanNotebook demo, AutoContextMemory dedicated demo, Bailian long-term memory
**Approach**: Minimal-change — extend AgentConfig + AgentFactory + agents.yml, no new service classes

## 1. PlanNotebook Integration

**Goal**: Upgrade `project-planner` agent to use AgentScope's built-in PlanNotebook with 10 planning tools.

### Changes

**AgentConfig.java** — add field:
```java
private boolean planEnabled = false;
```

**AgentFactory.buildAgent()** — after builder creation, before registerToolsAndSkills:
```java
if (config.isPlanEnabled()) {
    builder.enablePlan();
    log.info("  Enabled PlanNotebook for agent: {}", agentId);
}
```

**agents.yml** — upgrade `project-planner`:
- Remove `plan_notebook` from `systemTools` (was a no-op placeholder)
- Add `planEnabled: true`
- Keep existing autoContext config

### Frontend

No changes needed. PlanNotebook's 10 built-in tools (create_plan, finish_subtask, etc.) flow through existing tool_start/tool_end hook events. Debug Panel renders them as standard tool call cards.

### Demo Scenario

1. User: "帮我规划一个电商系统的开发计划"
2. Agent calls `create_plan` with subtasks → Debug Panel shows the plan creation
3. Agent executes subtasks one by one, calling `finish_subtask` after each
4. Agent calls `finish_plan` when complete

## 2. AutoContextMemory Demo

**Goal**: Dedicated `long-conversation` agent demonstrating context compression in long sessions.

### Changes

**agents.yml** — add new agent:
```yaml
- agentId: long-conversation
  category: single
  name: 长对话助手
  description: 支持超长对话的技术顾问，自动压缩上下文
  systemPrompt: |
    你是一个技术顾问助手，擅长各种技术领域的深入讨论。
    你能够进行长时间的多轮对话，始终保持连贯性和准确性。
    当对话变得很长时，系统会自动管理上下文，你不需要特殊处理。
  modelName: qwen-plus
  autoContext: true
  autoContextMsgThreshold: 20
  autoContextLastKeep: 8
  autoContextTokenRatio: 0.3
  enableThinking: true
```

No code changes. AgentFactory already handles AutoContextMemory creation and ContextOffloadTool registration.

### Frontend

No changes. ContextOffloadTool calls appear as standard tool_start/tool_end events in Debug Panel.

### Demo Scenario

1. User engages in 20+ turn technical Q&A
2. After threshold, `context_offload` tool fires (visible in Debug Panel)
3. Agent continues normally, context compressed transparently
4. When offloaded content is needed, `context_reload` fires

## 3. Long-term Memory (Bailian)

**Goal**: Cross-session preference recall using BailianLongTermMemory, reusing existing DASHSCOPE_API_KEY.

### Changes

**pom.xml** — add dependency:
```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-bailian-memory</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

**AgentConfig.java** — add nested config class:
```java
// Long-term memory settings
private LongTermMemoryConfig longTermMemory;

@Setter
@Getter
public static class LongTermMemoryConfig {
    private String type = "none";       // none, bailian
    private String mode = "STATIC_CONTROL";  // STATIC_CONTROL, AGENT_CONTROL, BOTH
    private String userId = "default_user";
}
```

**AgentFactory.java** — add long-term memory creation and builder wiring:
```java
// In buildAgent(), after RAG config:

// Configure long-term memory if enabled
if (config.getLongTermMemory() != null && !"none".equals(config.getLongTermMemory().getType())) {
    LongTermMemory ltm = createLongTermMemory(config.getLongTermMemory());
    if (ltm != null) {
        LongTermMemoryMode mode = parseLtmMode(config.getLongTermMemory().getMode());
        builder.longTermMemory(ltm).longTermMemoryMode(mode);
        log.info("  Enabled long-term memory for agent: {} (type={}, mode={})",
                agentId, config.getLongTermMemory().getType(), mode);
    }
}
```

New private method in AgentFactory:
```java
private LongTermMemory createLongTermMemory(LongTermMemoryConfig config) {
    return switch (config.getType().toLowerCase()) {
        case "bailian" -> BailianLongTermMemory.builder()
                .apiKey(apiKey)
                .userId(config.getUserId())
                .build();
        default -> null;
    };
}

private LongTermMemoryMode parseLtmMode(String value) {
    if (value == null) return LongTermMemoryMode.STATIC_CONTROL;
    return switch (value.trim().toUpperCase()) {
        case "AGENT_CONTROL" -> LongTermMemoryMode.AGENT_CONTROL;
        case "BOTH" -> LongTermMemoryMode.BOTH;
        default -> LongTermMemoryMode.STATIC_CONTROL;
    };
}
```

**agents.yml** — add new agent:
```yaml
- agentId: personal-assistant
  category: single
  name: 个人助手
  description: 跨会话记住你的偏好和习惯
  longTermMemory:
    type: bailian
    mode: STATIC_CONTROL
    userId: default_user
```

### ObservabilityHook — memory events

Add two new event types for long-term memory visibility:

```java
public static final String MEMORY_RECORD = "memory_record";
public static final String MEMORY_RECALL = "memory_recall";
```

However, BailianLongTermMemory in STATIC_CONTROL mode runs recall/record outside the hook lifecycle (before PreCallEvent and after PostCallEvent). Since the framework's hook system doesn't expose these as hook events, we will NOT add custom hook events for now. Instead:

- In AGENT_CONTROL or BOTH mode, recall/record tools appear as standard tool calls → Debug Panel shows them automatically
- In STATIC_CONTROL mode, the memory operations are transparent (no Debug Panel visibility)

This keeps the implementation simple. If STATIC_CONTROL observability is needed later, it can be added via a wrapping approach.

### Demo Scenario

1. User: "我喜欢用中文回答，格式用 Markdown，技术栈偏好 Java"
2. Agent responds and Bailian records preferences
3. User creates new session with `personal-assistant`
4. User: "按我的偏好介绍一下你自己"
5. Agent recalls preferences and responds in Chinese Markdown with Java focus

## 4. File Change Summary

| File | Change |
|------|--------|
| `pom.xml` | Add `agentscope-extensions-bailian-memory` dependency |
| `AgentConfig.java` | Add `planEnabled`, `LongTermMemoryConfig` inner class |
| `AgentFactory.java` | Add PlanNotebook enable, long-term memory creation, parseLtmMode |
| `agents.yml` | Upgrade `project-planner`, add `long-conversation`, add `personal-assistant` |
| `ROADMAP.md` | Update P3 status to completed |

No frontend changes required. All new capabilities are observable through existing Debug Panel tool event rendering.

## 5. Acceptance Criteria

- [ ] `project-planner` creates multi-step plans with visible create_plan/finish_subtask/finish_plan tool calls in Debug Panel
- [ ] `long-conversation` sustains 20+ turns with context_offload events visible in Debug Panel
- [ ] `personal-assistant` recalls user preferences across sessions via Bailian long-term memory
- [ ] `mvn test` passes with 0 failures
- [ ] All three agents appear in UI with proper category and sample prompts

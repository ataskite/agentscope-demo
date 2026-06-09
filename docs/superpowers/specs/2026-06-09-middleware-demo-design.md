# AgentScope 2.0 Middleware Demo Design

## Goal

Create a dedicated demo agent showcasing the AgentScope 2.0 Middleware system — an onion-pattern interception layer at 5 points in the agent lifecycle.

## Middleware API Overview

`MiddlewareBase` provides 5 hooks, all with default no-op implementations:

| Hook | Pattern | Purpose |
|------|---------|---------|
| `onSystemPrompt(agent, prompt)` | Pipeline | Sequential prompt transformation, returns `Mono<String>` |
| `onAgent(agent, AgentInput, next)` | Onion | Wraps entire agent execution, returns `Flux<AgentEvent>` |
| `onReasoning(agent, ReasoningInput, next)` | Onion | Wraps LLM reasoning phase |
| `onModelCall(agent, ModelCallInput, next)` | Onion | Wraps raw model API call |
| `onActing(agent, ActingInput, next)` | Onion | Wraps individual tool execution |

Onion pattern: each middleware receives `(agent, input, next)` where calling `next.apply(input)` proceeds to the next layer. Middleware can add before/after logic around `next`.

Registration: `ReActAgent.builder().middleware(mw).middlewares(List.of(mw1, mw2))`.

## Demo Middlewares

### 1. AuditLoggingMiddleware

**Hooks:** `onAgent`, `onReasoning`, `onActing`

**Behavior:**
- `onAgent`: Log agent name, input message count, total execution time
- `onReasoning`: Log model name, input message count, reasoning duration
- `onActing`: Log each tool call name, params preview, execution duration

All logs use SLF4J (visible in console). The middleware wraps the agent execution, so the existing ObservabilityHook still captures the standard lifecycle events (agent_start, llm_start, tool_start etc.) in the debug panel. No new frontend work needed.

**Class:** `com.skloda.agentscope.middleware.AuditLoggingMiddleware`

### 2. RateLimitMiddleware

**Hook:** `onModelCall`

**Behavior:**
- Track model call timestamps in a sliding window
- When rate limit exceeded, short-circuit by returning a `Flux` with a single text event explaining the limit
- Configurable: max calls per minute (default: 10)

**Class:** `com.skloda.agentscope.middleware.RateLimitMiddleware`

### 3. ContextEnrichmentMiddleware

**Hook:** `onSystemPrompt`

**Behavior:**
- Append dynamic context to the system prompt:
  - Current date/time
  - User identity (from ThreadLocal or session attribute)
  - Conversation turn number (from session state)
- Does not modify the original prompt, only appends a `<context>` section

**Class:** `com.skloda.agentscope.middleware.ContextEnrichmentMiddleware`

## Configuration

Add to `agents.yml`:

```yaml
- agentId: middleware-demo
  category: demo
  type: SINGLE
  name: 中间件演示助手
  description: 展示 AgentScope 2.0 中间件系统：审计日志、限流、上下文注入
  modelName: qwen-plus
  streaming: true
  enableThinking: true
  middlewares:
    - audit-logging
    - rate-limit
    - context-enrichment
  systemPrompt: |
    你是一个中间件演示助手。你的每次调用都会经过审计日志、限流检查和上下文注入中间件。
    请正常回答用户的问题，展示中间件的工作效果。
  samplePrompts:
    - prompt: "你好，请问现在几点了？"
      expectedBehavior: "ContextEnrichmentMiddleware 注入当前时间，AuditLoggingMiddleware 记录调用日志"
    - prompt: "连续问我10个问题测试限流"
      expectedBehavior: "RateLimitMiddleware 在超过阈值时返回限流提示"
```

## Integration Points

### AgentConfig

Add `middlewares` field (List<String>) to `AgentConfig`.

### AgentFactory

In `buildAgent()`, after creating the ReActAgent.Builder, resolve middleware names to instances via a `MiddlewareRegistry` and register with `.middlewares(list)`.

### MiddlewareRegistry

Simple map of name -> supplier, similar to existing `ToolRegistry`:

```java
@Component
public class MiddlewareRegistry {
    private final Map<String, Supplier<MiddlewareBase>> registry = new LinkedHashMap<>();

    public void register(String name, Supplier<MiddlewareBase> factory) { ... }
    public MiddlewareBase create(String name) { ... }
}
```

Auto-registers the 3 demo middlewares. Extensible for future additions.

### Frontend

No frontend changes required. Middleware events flow through the existing ObservabilityHook → SSE → debug panel pipeline. The debug panel timeline will naturally show the middleware lifecycle events.

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `middleware/AuditLoggingMiddleware.java` | New | Audit logging middleware |
| `middleware/RateLimitMiddleware.java` | New | Rate limiting middleware |
| `middleware/ContextEnrichmentMiddleware.java` | New | Context enrichment middleware |
| `middleware/MiddlewareRegistry.java` | New | Middleware name → instance registry |
| `agent/AgentConfig.java` | Modify | Add `middlewares` field |
| `agent/AgentFactory.java` | Modify | Resolve and register middlewares |
| `config/agents.yml` | Modify | Add middleware-demo agent config |

## Out of Scope

- Middleware configuration per-middleware (e.g., custom rate limits per agent) — use hardcoded defaults
- Frontend UI for middleware management
- Persisting middleware state across sessions

# AgentScope 2.0 Phase 1: 基础升级 & 废弃清理 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 升级到 AgentScope 2.0.0-RC3，清理所有 Part B 废弃 API（除 RAG/LTM 暂不迁移），确保编译通过且现有功能正常。

**Architecture:** 分两批执行：第一批是简单清理（版本升级、删除废弃方法/调用），第二批是核心架构迁移（Hook → Middleware、stream → streamEvents）。第二批是主要工作量，需要重构运行时层的事件桥接机制。

**Tech Stack:** Java 17, Spring Boot 3.5.14, AgentScope 2.0.0-RC3, Project Reactor

---

## 文件影响范围

### 第一批：简单清理

| 文件 | 操作 | 说明 |
|------|------|------|
| `pom.xml` | 修改 | 版本号 RC1 → RC3 |
| `src/.../agent/AgentFactory.java` | 修改 | 删除 structuredOutputReminder、迁移 SkillBox |
| `src/.../runtime/AgentRuntimeFactory.java` | 修改 | 删除 Memory 系列废弃方法 |
| `src/.../composite/CompositeAgentFactory.java` | 修改 | 删除 Memory 系列废弃方法 |

### 第二批：核心架构迁移

| 文件 | 操作 | 说明 |
|------|------|------|
| `src/.../runtime/EventSink.java` | 新建 | 轻量事件总线，替代 ObservabilityHook 的手动发射部分 |
| `src/.../middleware/ObservabilityMiddleware.java` | 新建 | 继承 MiddlewareBase，替代 ObservabilityHook |
| `src/.../middleware/ApprovalMiddleware.java` | 新建 | 继承 MiddlewareBase，替代 ApprovalHook |
| `src/.../hook/ObservabilityHook.java` | 删除 | 被 ObservabilityMiddleware 替代 |
| `src/.../hook/ApprovalHook.java` | 删除 | 被 ApprovalMiddleware 替代 |
| `src/.../runtime/AgentRuntime.java` | 重写 | 使用 streamEvents() + AgentEvent 替代 stream() + Hook |
| `src/.../runtime/StructuredOutputAgentRuntime.java` | 修改 | 适配新 EventSink |
| `src/.../runtime/StateGraphRuntime.java` | 修改 | 适配新 EventSink |
| `src/.../runtime/StreamingAgentRuntime.java` | 修改 | 接口返回类型更新 |
| `src/.../runtime/AgentRuntimeFactory.java` | 修改 | Hook → Middleware、废弃方法已删 |
| `src/.../composite/CompositeAgentFactory.java` | 修改 | Hook → Middleware |
| `src/.../agent/AgentFactory.java` | 修改 | hooks → middlewares 参数 |
| `src/.../service/AgentService.java` | 修改 | 适配新 runtime API |
| `src/.../service/ApprovalService.java` | 修改 | 适配 ApprovalMiddleware |

---

## Task 1: 版本升级 RC1 → RC3

**Files:**
- Modify: `pom.xml:25`

- [ ] **Step 1: 更新 pom.xml 版本号**

```xml
<!-- 将 -->
<agentscope.version>2.0.0-RC1</agentscope.version>
<!-- 改为 -->
<agentscope.version>2.0.0-RC3</agentscope.version>
```

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add pom.xml
git commit -m "chore: upgrade AgentScope from 2.0.0-RC1 to 2.0.0-RC3"
```

---

## Task 2: 删除 structuredOutputReminder 调用

**背景:** AgentScope 2.0 中 `StructuredOutputReminder` 已删除（A.1），模型层原生支持结构化输出。当前代码在 `AgentFactory.java` 第 140-146 行和第 223-230 行使用了此 API。

**Files:**
- Modify: `src/.../agent/AgentFactory.java:139-146,223-230`
- Modify: `src/.../agent/AgentFactory.java:20` (删除 import)

- [ ] **Step 1: 删除 buildAgent 中的 structuredOutputReminder 块**

在 `AgentFactory.java` 的 `buildAgent` 方法中（约第 139-146 行），删除以下代码块:

```java
// 删除这整个 if 块:
if (config.getStructuredOutputClass() != null && !config.getStructuredOutputClass().isBlank()) {
    StructuredOutputReminder reminder = "PROMPT".equalsIgnoreCase(config.getStructuredOutputReminder())
            ? StructuredOutputReminder.PROMPT
            : StructuredOutputReminder.TOOL_CHOICE;
    builder.structuredOutputReminder(reminder);
    log.info("  Configured structured output for agent: {} (class={}, mode={})",
            agentId, config.getStructuredOutputClass(), reminder);
}
```

- [ ] **Step 2: 删除 buildAgentWithPermission 中的相同块**

在 `buildAgentWithPermission` 方法中（约第 223-230 行），删除同样的代码块。

- [ ] **Step 3: 删除废弃 import**

从 `AgentFactory.java` 文件顶部删除:
```java
import io.agentscope.core.model.StructuredOutputReminder;
```

- [ ] **Step 4: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS（如果 StructuredOutputReminder 已从 RC3 中删除则报错；如果仍存在则通过——无论如何代码已清理）

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/skloda/agentscope/agent/AgentFactory.java
git commit -m "refactor: remove deprecated structuredOutputReminder calls"
```

---

## Task 3: 删除废弃的 Memory 系列方法

**背景:** `AgentRuntimeFactory` 中有 `@Deprecated` 的 `createRuntimeWithMemory()` 及相关方法。`CompositeAgentFactory` 中有 `@Deprecated` 的 Memory 参数重载。

**Files:**
- Modify: `src/.../runtime/AgentRuntimeFactory.java`
- Modify: `src/.../composite/CompositeAgentFactory.java`

- [ ] **Step 1: 删除 AgentRuntimeFactory 中的废弃方法**

删除以下方法:
- `createRuntimeWithMemory(String agentId, Memory memory)` (第 78-96 行)
- `createRoutingRuntimeWithMemory(String agentId, Memory memory)` (第 143-148 行)
- `createHandoffsRuntimeWithMemory(String agentId, Memory memory)` (第 157-162 行)
- `createStateGraphRuntimeWithMemory(String agentId, Memory memory)` (第 171-176 行)
- `createHarnessRuntimeWithMemory(String agentId, Memory memory)` (第 183-186 行)
- `createSingleRuntimeWithMemory(String agentId, Memory memory)` (第 288-301 行)

同时删除 `import io.agentscope.core.memory.Memory;`（如果不再被其他方法使用）。

- [ ] **Step 2: 删除 CompositeAgentFactory 中的废弃方法**

删除以下 `@Deprecated` 方法:
- `createSingleAgentForSession(String agentId, Memory memory, Hook... hooks)` (第 112-114 行)
- `createSingleAgentForSession(String agentId, Memory memory, Hook hook, ApprovalHook approvalHook)` (第 120-122 行)
- `createStateGraphAgent(AgentConfig config, Memory memory)` (第 161-163 行)
- `createRoutingAgent(AgentConfig config, Memory memory, Hook... hooks)` (第 258-260 行)
- `createHandoffsAgent(AgentConfig config, Memory memory, Hook... hooks)` (第 387-389 行)

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/skloda/agentscope/runtime/AgentRuntimeFactory.java src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java
git commit -m "refactor: remove deprecated Memory-based runtime creation methods"
```

---

## Task 4: SkillBox → SkillRepository 迁移

**背景:** `SkillBox` + `.skillBox(skillBox)` 标记 `@Deprecated(forRemoval = true)`。替代方案是 `ClasspathSkillRepository` / `FileSystemSkillRepository` + `.skillRepository()`。注册 repository 后 `DynamicSkillMiddleware` 自动安装。

**Files:**
- Modify: `src/.../agent/AgentFactory.java:289-335`

- [ ] **Step 1: 重写 registerToolsAndSkills 方法**

将 `AgentFactory.java` 中的 `registerToolsAndSkills` 方法重写为:

```java
private void registerToolsAndSkills(ReActAgent.Builder builder, Toolkit toolkit,
                                     AgentConfig config, String agentId) {
    Set<String> skillToolNames = new HashSet<>();

    if (!config.getSkills().isEmpty()) {
        try (ClasspathSkillRepository repo = new ClasspathSkillRepository("skills")) {
            for (String skillName : config.getSkills()) {
                if (toolRegistry.hasTool(skillName)) {
                    repo.getSkill(skillName); // validate skill exists
                    toolkit.registerTool(toolRegistry.getTool(skillName));
                    skillToolNames.addAll(toolRegistry.getToolNamesForClass(skillName));
                    log.info("  Registered skill: {} for agent: {}", skillName, agentId);
                } else {
                    log.error("  Tool for skill not found in registry: {} (agent: {})", skillName, agentId);
                }
            }
            builder.skillRepository(repo);
        } catch (Exception e) {
            log.error("  Failed to load skills for agent: {}", agentId, e);
        }
    }

    List<String> userToolsFiltered = config.getUserTools().stream()
            .filter(name -> !skillToolNames.contains(name))
            .toList();
    for (Object toolInstance : toolRegistry.getDeduplicatedInstances(userToolsFiltered)) {
        toolkit.registerTool(toolInstance);
        log.info("  Registered user tool class: {} for agent: {}", toolInstance.getClass().getSimpleName(), agentId);
    }
    if (!skillToolNames.isEmpty()) {
        log.info("  Skipped user tools already covered by skills: {} (agent: {})", skillToolNames, agentId);
    }

    for (Object toolInstance : toolRegistry.getDeduplicatedInstances(config.getSystemTools())) {
        toolkit.registerTool(toolInstance);
        log.info("  Registered system tool class: {} for agent: {}", toolInstance.getClass().getSimpleName(), agentId);
    }

    builder.toolkit(toolkit);
}
```

- [ ] **Step 2: 更新 import**

从 `AgentFactory.java` 删除:
```java
import io.agentscope.core.skill.SkillBox;
```

保留:
```java
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
```

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/skloda/agentscope/agent/AgentFactory.java
git commit -m "refactor: migrate SkillBox to SkillRepository (2.0 API)"
```

---

## Task 5: 创建 EventSink 轻量事件总线

**背景:** 当前 `ObservabilityHook` 有两种用途:
1. 通过 `Hook.onEvent()` 自动捕获 agent 生命周期事件 → 将被 `streamEvents()` 替代
2. 通过 `emitXxx()` 方法手动发射多 Agent 事件（pipeline_start、routing_decision 等） → 需要保留

创建 `EventSink` 替代用途 2，作为纯粹的消费者通知机制。

**Files:**
- Create: `src/main/java/com/skloda/agentscope/runtime/EventSink.java`

- [ ] **Step 1: 创建 EventSink**

```java
package com.skloda.agentscope.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Lightweight event bus for SSE event emission.
 * Replaces the manual emit() portion of ObservabilityHook.
 * Lifecycle events now come from agent.streamEvents() AgentEvent stream.
 */
public class EventSink {

    private static final Logger log = LoggerFactory.getLogger(EventSink.class);

    // Multi-agent event type constants
    public static final String PIPELINE_START = "pipeline_start";
    public static final String PIPELINE_STEP_START = "pipeline_step_start";
    public static final String PIPELINE_STEP_END = "pipeline_step_end";
    public static final String PIPELINE_END = "pipeline_end";
    public static final String ROUTING_DECISION = "routing_decision";
    public static final String HANDOFF_START = "handoff_start";
    public static final String HANDOFF_COMPLETE = "handoff_complete";
    public static final String LOOP_START = "loop_start";
    public static final String LOOP_END = "loop_end";
    public static final String LOOP_ITERATION_RESULT = "loop_iteration_result";
    public static final String GRAPH_TRANSITION = "graph_transition";
    public static final String GRAPH_AGENT_CALL = "graph_agent_call";
    public static final String ROUNDTABLE_START = "roundtable_start";
    public static final String ROUND_START = "round_start";
    public static final String ROUND_END = "round_end";
    public static final String ROUND_MESSAGE = "round_message";
    public static final String ROUNDTABLE_SUMMARY = "roundtable_summary";
    public static final String TASK_DELEGATE = "task_delegate";
    public static final String TASK_START = "task_start";
    public static final String TASK_END = "task_end";
    public static final String TASK_AGGREGATE = "task_aggregate";

    private final Sinks.Many<Map<String, Object>> sink = Sinks.many().multicast().onBackpressureBuffer();
    private final List<BiConsumer<String, Map<String, Object>>> consumers = Collections.synchronizedList(new ArrayList<>());

    public void addConsumer(BiConsumer<String, Map<String, Object>> consumer) {
        consumers.add(consumer);
    }

    public void removeConsumer(BiConsumer<String, Map<String, Object>> consumer) {
        consumers.remove(consumer);
    }

    public void emit(String type, Map<String, Object> data) {
        log.debug("[event-sink] {}: {}", type, data);
        Map<String, Object> payload = new LinkedHashMap<>(data);
        payload.put("type", type);
        Sinks.EmitResult result = sink.tryEmitNext(payload);
        if (result.isFailure()) {
            log.warn("Failed to emit event: {}", result);
        }
        for (BiConsumer<String, Map<String, Object>> consumer : consumers) {
            try {
                consumer.accept(type, data);
            } catch (Exception e) {
                log.warn("Consumer error for event {}: {}", type, e.getMessage());
            }
        }
    }

    public reactor.core.publisher.Flux<Map<String, Object>> asFlux() {
        return sink.asFlux();
    }

    public void complete() {
        sink.tryEmitComplete();
    }

    // ---- Multi-agent event convenience methods ----

    public void emitPipelineStart(String pipelineId, List<String> subAgents) {
        emit(PIPELINE_START, Map.of("pipelineId", pipelineId, "subAgents", subAgents, "timestamp", System.currentTimeMillis()));
    }

    public void emitPipelineStepStart(String pipelineId, int stepIndex, String agentId) {
        emit(PIPELINE_STEP_START, Map.of("pipelineId", pipelineId, "stepIndex", stepIndex, "agentId", agentId, "timestamp", System.currentTimeMillis()));
    }

    public void emitPipelineStepEnd(String pipelineId, int stepIndex, String agentId, long durationMs) {
        emit(PIPELINE_STEP_END, Map.of("pipelineId", pipelineId, "stepIndex", stepIndex, "agentId", agentId, "duration_ms", durationMs, "timestamp", System.currentTimeMillis()));
    }

    public void emitPipelineEnd(String pipelineId, int totalSteps, long totalDurationMs) {
        emit(PIPELINE_END, Map.of("pipelineId", pipelineId, "totalSteps", totalSteps, "duration_ms", totalDurationMs, "timestamp", System.currentTimeMillis()));
    }

    public void emitRoutingDecision(String routingId, String selectedAgent, String reasoning) {
        emit(ROUTING_DECISION, Map.of("routingId", routingId, "selectedAgent", selectedAgent, "reasoning", reasoning, "timestamp", System.currentTimeMillis()));
    }

    public void emitHandoffStart(String fromAgent, String toAgent, String reason) {
        emit(HANDOFF_START, Map.of("fromAgent", fromAgent, "toAgent", toAgent, "reason", reason, "timestamp", System.currentTimeMillis()));
    }

    public void emitHandoffComplete(String fromAgent, String toAgent) {
        emit(HANDOFF_COMPLETE, Map.of("fromAgent", fromAgent, "toAgent", toAgent, "timestamp", System.currentTimeMillis()));
    }

    public void emitGraphTransition(String fromState, String toState, String trigger) {
        emit(GRAPH_TRANSITION, Map.of("fromState", fromState, "toState", toState, "trigger", trigger, "timestamp", System.currentTimeMillis()));
    }

    public void emitGraphAgentCall(String state, String agent) {
        emit(GRAPH_AGENT_CALL, Map.of("state", state, "agent", agent, "timestamp", System.currentTimeMillis()));
    }

    public void emitLoopStart(int iteration) {
        emit(LOOP_START, Map.of("iteration", iteration, "timestamp", System.currentTimeMillis()));
    }

    public void emitLoopEnd(int totalIterations, boolean finalApproved) {
        emit(LOOP_END, Map.of("totalIterations", totalIterations, "finalApproved", finalApproved, "timestamp", System.currentTimeMillis()));
    }

    public void emitLoopIterationResult(int iteration, boolean approved, String feedback) {
        emit(LOOP_ITERATION_RESULT, Map.of("iteration", iteration, "approved", approved, "feedback", feedback, "timestamp", System.currentTimeMillis()));
    }

    public void emitRoundtableStart(String pipelineId, List<String> participants, int rounds) {
        emit(ROUNDTABLE_START, Map.of("pipelineId", pipelineId, "participants", participants, "rounds", rounds, "timestamp", System.currentTimeMillis()));
    }

    public void emitRoundStart(int round) {
        emit(ROUND_START, Map.of("round", round, "timestamp", System.currentTimeMillis()));
    }

    public void emitRoundEnd(int round) {
        emit(ROUND_END, Map.of("round", round, "timestamp", System.currentTimeMillis()));
    }

    public void emitRoundMessage(String agentId, String content) {
        emit(ROUND_MESSAGE, Map.of("agent", agentId, "content", content, "timestamp", System.currentTimeMillis()));
    }

    public void emitRoundtableSummary(String agentId, String content) {
        emit(ROUNDTABLE_SUMMARY, Map.of("agent", agentId, "content", content, "timestamp", System.currentTimeMillis()));
    }

    public void emitTaskDelegate(String from, String to, String task) {
        emit(TASK_DELEGATE, Map.of("from", from, "to", to, "task", task, "timestamp", System.currentTimeMillis()));
    }

    public void emitTaskStart(String agent) {
        emit(TASK_START, Map.of("agent", agent, "timestamp", System.currentTimeMillis()));
    }

    public void emitTaskEnd(String agent, String outputPreview) {
        emit(TASK_END, Map.of("agent", agent, "outputPreview", outputPreview, "timestamp", System.currentTimeMillis()));
    }

    public void emitTaskAggregate(int totalTasks) {
        emit(TASK_AGGREGATE, Map.of("totalTasks", totalTasks, "timestamp", System.currentTimeMillis()));
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/skloda/agentscope/runtime/EventSink.java
git commit -m "feat: add EventSink for lightweight SSE event emission"
```

---

## Task 6: 重写 AgentRuntime 使用 streamEvents()

**背景:** 这是 Phase 1 的核心改动。当前 `AgentRuntime.stream()` 使用:
1. `agent.stream(userMsg, streamOptions)` (deprecated) 获取 `Flux<Event>`
2. `ObservabilityHook` + consumer 桥接获取生命周期事件

迁移后:
1. `agent.streamEvents(userMsg)` 获取 `Flux<AgentEvent>`，包含所有生命周期事件
2. `EventSink` 用于手动多 Agent 事件
3. 合并两个流

**Files:**
- Modify: `src/.../runtime/AgentRuntime.java` (完全重写)
- Modify: `src/.../runtime/StreamingAgentRuntime.java` (接口更新)

- [ ] **Step 1: 更新 StreamingAgentRuntime 接口**

将 `StreamingAgentRuntime.java` 改为:

```java
package com.skloda.agentscope.runtime;

import io.agentscope.core.message.Msg;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Common streaming contract for both single-agent and composite-agent runtimes.
 */
public interface StreamingAgentRuntime extends AutoCloseable {

    Flux<Map<String, Object>> stream(Msg userMsg);

    EventSink getEventSink();

    @Override
    void close();
}
```

- [ ] **Step 2: 重写 AgentRuntime**

将 `AgentRuntime.java` 完全重写为以下内容。核心变化:
- 不再使用 `ObservabilityHook`，改用 `EventSink`
- 使用 `agent.streamEvents()` 替代 `agent.stream()`
- 将 `AgentEvent` 类型映射到现有 SSE 事件格式
- 保留 HITL 审批流程（ApprovalMiddleware 在 Task 7 中实现，此处先保留 hook 方式的兼容）

```java
package com.skloda.agentscope.runtime;

import com.skloda.agentscope.service.ApprovalService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.ReasoningStartEvent;
import io.agentscope.core.event.ReasoningEndEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runtime container using streamEvents() + EventSink.
 * Agent lifecycle events come from agent.streamEvents() AgentEvent stream.
 * Multi-agent orchestration events come from EventSink manual emission.
 */
public class AgentRuntime implements StreamingAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    @Getter
    private final ReActAgent agent;
    @Getter
    private final EventSink eventSink;
    private final String agentId;
    private final String sessionId;
    private final Runnable onClose;

    public AgentRuntime(ReActAgent agent) {
        this(agent, new EventSink(), null, null, null);
    }

    public AgentRuntime(ReActAgent agent, EventSink eventSink) {
        this(agent, eventSink, null, null, null);
    }

    public AgentRuntime(ReActAgent agent, EventSink eventSink, String agentId,
                        ApprovalService approvalService, String sessionId) {
        this(agent, eventSink, agentId, approvalService, sessionId, null);
    }

    public AgentRuntime(ReActAgent agent, EventSink eventSink, String agentId,
                        ApprovalService approvalService, String sessionId,
                        Runnable onClose) {
        this.agent = agent;
        this.eventSink = eventSink;
        this.agentId = agentId;
        this.sessionId = sessionId;
        this.onClose = onClose;
    }

    @Override
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        return stream(userMsg, false);
    }

    public Flux<Map<String, Object>> stream(Msg userMsg, boolean isApprovalResume) {
        log.debug("Starting stream for agent: {} (resume={})", agent.getName(), isApprovalResume);

        Flux<Map<String, Object>> sinkEvents = eventSink.asFlux()
                .doOnNext(payload -> log.debug("[sink -> stream] {}", payload));

        Flux<Map<String, Object>> agentEvents;
        if (isApprovalResume) {
            agentEvents = Flux.create(fluxSink -> {
                Mono<Msg> resumeCall = userMsg != null
                        ? agent.call(userMsg)
                        : agent.call();
                resumeCall.subscribe(
                        msg -> emitTextFromMsg(msg, fluxSink),
                        error -> {
                            log.error("Resume stream error", error);
                            fluxSink.error(error);
                        },
                        () -> completeStream(fluxSink)
                );
            });
        } else {
            agentEvents = agent.streamEvents(userMsg)
                    .flatMap(event -> Flux.fromIterable(mapAgentEvent(event)));
        }

        return Flux.merge(sinkEvents, agentEvents)
                .doOnCancel(this::close)
                .doOnComplete(() -> {
                    eventSink.complete();
                    this.close();
                })
                .doOnError(e -> {
                    log.error("Stream error for agent: {}", agent.getName(), e);
                    eventSink.complete();
                    this.close();
                });
    }

    private java.util.List<Map<String, Object>> mapAgentEvent(AgentEvent event) {
        java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();

        try {
            switch (event.getType()) {
                case TEXT_BLOCK_DELTA -> {
                    if (event instanceof TextBlockDeltaEvent e) {
                        String delta = e.getDelta();
                        if (delta != null && !delta.isEmpty()) {
                            results.add(Map.of("type", "text", "content", delta));
                        }
                    }
                }
                case THINKING_BLOCK_DELTA -> {
                    if (event instanceof ThinkingBlockDeltaEvent e) {
                        String thinking = e.getDelta();
                        if (thinking != null && !thinking.isEmpty()) {
                            results.add(Map.of("type", "thinking", Map.of("content", thinking)));
                        }
                    }
                }
                case AGENT_START -> {
                    if (event instanceof AgentStartEvent e) {
                        results.add(Map.of(
                                "type", "agent_start",
                                "agentName", e.getAgentName() != null ? e.getAgentName() : "",
                                "timestamp", System.currentTimeMillis()
                        ));
                    }
                }
                case AGENT_END -> {
                    if (event instanceof AgentEndEvent e) {
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("type", "agent_end");
                        data.put("timestamp", System.currentTimeMillis());
                        results.add(data);
                    }
                }
                case TOOL_CALL_START -> {
                    if (event instanceof ToolCallStartEvent e) {
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("type", "tool_start");
                        data.put("id", e.getToolCallId() != null ? e.getToolCallId() : "");
                        data.put("name", e.getToolCallName() != null ? e.getToolCallName() : "");
                        data.put("timestamp", System.currentTimeMillis());
                        results.add(data);
                        log.info("[tool_start] {}", e.getToolCallName());
                    }
                }
                case TOOL_CALL_END -> {
                    if (event instanceof ToolCallEndEvent e) {
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("type", "tool_end");
                        data.put("id", e.getToolCallId() != null ? e.getToolCallId() : "");
                        data.put("name", e.getToolCallName() != null ? e.getToolCallName() : "");
                        data.put("timestamp", System.currentTimeMillis());
                        results.add(data);
                        log.info("[tool_end] {}", e.getToolCallName());
                    }
                }
                case ERROR -> {
                    results.add(Map.of(
                            "type", "error",
                            "message", event.toString(),
                            "timestamp", System.currentTimeMillis()
                    ));
                }
                default -> {
                    // Other event types (REASONING_START, REASONING_END, etc.) can be mapped as needed
                    log.debug("Unhandled AgentEvent type: {}", event.getType());
                }
            }
        } catch (Exception e) {
            log.error("Error mapping AgentEvent", e);
        }

        return results;
    }

    private void emitTextFromMsg(Msg msg, reactor.core.publisher.FluxSink<Map<String, Object>> fluxSink) {
        if (msg == null || msg.getContent() == null) return;
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb) {
                String text = tb.getText();
                if (text != null && !text.isEmpty()) {
                    fluxSink.next(Map.of("type", "text", "content", text));
                }
            }
        }
    }

    private void completeStream(reactor.core.publisher.FluxSink<Map<String, Object>> fluxSink) {
        fluxSink.next(Map.of("type", "done"));
        fluxSink.complete();
    }

    private void emit(Map<String, Object> payload) {
        eventSink.emit((String) payload.get("type"), payload);
    }

    @Override
    public void close() {
        eventSink.complete();
        if (onClose != null) {
            onClose.run();
        }
        log.debug("AgentRuntime closed for agent: {}", agent.getName());
    }

    /**
     * Temporary compatibility hook for approval resume flow.
     * Will be replaced by ApprovalMiddleware in Task 7.
     */
    private static class StopAfterApprovedToolHook implements Hook {
        @Override
        public <T extends HookEvent> Mono<T> onEvent(T event) {
            if (event instanceof PostActingEvent postActingEvent) {
                postActingEvent.stopAgent();
            }
            return Mono.just(event);
        }
    }
}
```

**注意:** `AgentEvent` 的具体子类（`TextBlockDeltaEvent`、`ToolCallStartEvent` 等）需要根据 RC3 的实际 API 确认。上面的代码基于迁移文档描述的事件类型，如果 API 不匹配需要调整方法签名和字段名。

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -q`
Expected: 可能需要根据 RC3 的实际 `AgentEvent` API 调整 import 和类型名。反复修正直到 BUILD SUCCESS。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/skloda/agentscope/runtime/AgentRuntime.java src/main/java/com/skloda/agentscope/runtime/StreamingAgentRuntime.java
git commit -m "refactor: rewrite AgentRuntime to use streamEvents() + EventSink"
```

---

## Task 7: 适配 StructuredOutputAgentRuntime 和 StateGraphRuntime

**背景:** 这两个 runtime 也使用 `ObservabilityHook`，需要迁移到 `EventSink`。

**Files:**
- Modify: `src/.../runtime/StructuredOutputAgentRuntime.java`
- Modify: `src/.../runtime/StateGraphRuntime.java`

- [ ] **Step 1: 重写 StructuredOutputAgentRuntime 使用 EventSink**

将 `StructuredOutputAgentRuntime` 中的 `ObservabilityHook` 替换为 `EventSink`:
- 构造函数接受 `EventSink` 而非 `ObservabilityHook`
- 删除 `hookBridge` 相关代码，改为直接使用 `eventSink.emit()` 和 `eventSink.asFlux()`
- `stream()` 方法中合并 `eventSink.asFlux()` + 结果流
- `close()` 方法中调用 `eventSink.complete()`

- [ ] **Step 2: 重写 StateGraphRuntime 使用 EventSink**

同理，将 `StateGraphRuntime` 中的 `ObservabilityHook` 替换为 `EventSink`。

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/skloda/agentscope/runtime/StructuredOutputAgentRuntime.java src/main/java/com/skloda/agentscope/runtime/StateGraphRuntime.java
git commit -m "refactor: migrate StructuredOutput and StateGraph runtimes to EventSink"
```

---

## Task 8: 更新 AgentRuntimeFactory 和 CompositeAgentFactory

**背景:** 所有 runtime 创建方法需要从 Hook 切换到 EventSink。

**Files:**
- Modify: `src/.../runtime/AgentRuntimeFactory.java`
- Modify: `src/.../composite/CompositeAgentFactory.java`

- [ ] **Step 1: 更新 AgentRuntimeFactory**

核心变化:
- 所有 `new ObservabilityHook()` → `new EventSink()`
- `new AgentRuntime(agent, hook, ...)` → `new AgentRuntime(agent, eventSink, ...)`
- `new StructuredOutputAgentRuntime(agent, hook, ...)` → `new StructuredOutputAgentRuntime(agent, eventSink, ...)`
- `new StateGraphRuntime(agentId, graph, hook)` → `new StateGraphRuntime(agentId, graph, eventSink)`
- 删除 `import ...hook.ObservabilityHook` 和 `import ...hook.ApprovalHook`

ApprovalHook 临时保留为 Hook 方式（Builder.hooks()），后续 Task 9 中迁移为 Middleware。

- [ ] **Step 2: 更新 CompositeAgentFactory**

- 所有方法签名中的 `Hook... hooks` 参数改为通过 Builder 传递
- `builder.hooks(List.of(hooks))` → 如果有 Middleware 需求，改为 `builder.middlewares(...)`
- 暂时保留 `Hook` 参数用于 ApprovalHook 兼容，但 ObservabilityHook 不再需要传入

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/skloda/agentscope/runtime/AgentRuntimeFactory.java src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java
git commit -m "refactor: update runtime factories to use EventSink instead of Hook"
```

---

## Task 9: ApprovalHook → ApprovalMiddleware 迁移

**背景:** `ApprovalHook` 通过 `PostReasoningEvent.stopAgent()` 暂停 agent 执行。迁移到 Middleware 后，在 `onActing` 阶段拦截 tool 调用。

**Files:**
- Create: `src/.../middleware/ApprovalMiddleware.java`
- Delete: `src/.../hook/ApprovalHook.java`
- Modify: `src/.../runtime/AgentRuntimeFactory.java`
- Modify: `src/.../composite/CompositeAgentFactory.java`
- Modify: `src/.../agent/AgentFactory.java`
- Modify: `src/.../service/ApprovalService.java`

- [ ] **Step 1: 创建 ApprovalMiddleware**

```java
package com.skloda.agentscope.middleware;

import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.MiddlewareContext;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Middleware that intercepts tool calls requiring human approval.
 * Replaces ApprovalHook with MiddlewareBase onActing stage.
 */
public class ApprovalMiddleware extends MiddlewareBase {

    private final boolean approvalRequired;
    private final Set<String> approvalTools;
    private final AtomicBoolean approvalTriggered = new AtomicBoolean(false);
    private volatile List<ToolUseBlock> pendingToolUseBlocks = List.of();

    public ApprovalMiddleware(boolean approvalRequired, List<String> approvalTools) {
        this.approvalRequired = approvalRequired;
        this.approvalTools = approvalTools != null ? new HashSet<>(approvalTools) : Set.of();
    }

    public boolean needsApproval() {
        return approvalRequired || !approvalTools.isEmpty();
    }

    public boolean isApprovalTriggered() {
        return approvalTriggered.get();
    }

    public List<ToolUseBlock> getPendingToolUseBlocks() {
        return pendingToolUseBlocks;
    }

    public List<Map<String, Object>> getPendingToolCallsForSse() {
        return pendingToolUseBlocks.stream()
                .map(t -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", t.getId() != null ? t.getId() : "");
                    map.put("name", t.getName() != null ? t.getName() : "");
                    map.put("input", t.getInput() != null ? t.getInput().toString() : "{}");
                    map.put("inputParams", t.getInput() != null ? t.getInput() : Map.of());
                    return map;
                })
                .toList();
    }

    // Note: The actual onActing override depends on the MiddlewareBase API in RC3.
    // The method signature and how to intercept tool calls will need to be verified
    // against the actual agentscope-core 2.0.0-RC3 MiddlewareBase class.
    // The conceptual flow is:
    // 1. In onActing, check if the tool call requires approval
    // 2. If yes, set approvalTriggered and store pending tool blocks
    // 3. Return a signal that stops the agent execution
}
```

**重要:** `MiddlewareBase` 的 `onActing` 方法签名需要根据 RC3 的实际 API 实现。上面的代码是框架性的，需要查看 `MiddlewareBase` 的具体方法签名来完成。如果 `MiddlewareBase` 的 API 与预期不同，可能需要调整实现方式（例如使用 `onReasoning` 来检测 ToolUseBlock 并停止 agent）。

- [ ] **Step 2: 更新 AgentRuntimeFactory 使用 ApprovalMiddleware**

将 `createApprovalHookIfNeeded` 改为 `createApprovalMiddlewareIfNeeded`，返回 `ApprovalMiddleware`。
将 ApprovalMiddleware 通过 `builder.middlewares(...)` 传入，而非 `builder.hooks(...)`。

- [ ] **Step 3: 更新 CompositeAgentFactory**

删除 `mergeHooks` 辅助方法中的 `ApprovalHook` 参数。
改为通过 Middleware 列表传入。

- [ ] **Step 4: 删除 ApprovalHook.java**

```bash
rm src/main/java/com/skloda/agentscope/hook/ApprovalHook.java
```

- [ ] **Step 5: 更新 ApprovalService**

将 `ApprovalService` 中对 `ApprovalHook` 的引用改为 `ApprovalMiddleware`。

- [ ] **Step 6: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "refactor: migrate ApprovalHook to ApprovalMiddleware (2.0 API)"
```

---

## Task 10: 删除 ObservabilityHook 并清理所有 Hook 引用

**Files:**
- Delete: `src/.../hook/ObservabilityHook.java`
- Modify: `src/.../agent/AgentFactory.java` (hooks → middlewares)
- Modify: `src/.../composite/CompositeAgentFactory.java`
- Modify: `src/.../service/AgentService.java`
- Verify: 所有文件中不再有 `io.agentscope.core.hook` import

- [ ] **Step 1: 删除 ObservabilityHook**

```bash
rm src/main/java/com/skloda/agentscope/hook/ObservabilityHook.java
```

- [ ] **Step 2: 搜索所有 Hook 引用并清理**

Run: `grep -r "io.agentscope.core.hook" src/main/java/ --include="*.java" -l`

对每个找到的文件:
- 删除 `import io.agentscope.core.hook.*` 相关 import
- 将 `Hook` 参数替换为 `MiddlewareBase` 参数
- 将 `builder.hooks(...)` 替换为 `builder.middlewares(...)`

- [ ] **Step 3: 更新 AgentFactory**

在 `AgentFactory.java` 中:
- 将 `Hook... hooks` 参数改为 `MiddlewareBase... middlewares`
- 将 `builder.hooks(List.of(hooks))` 改为 `builder.middlewares(List.of(middlewares))`
- 删除 `import io.agentscope.core.hook.Hook`

- [ ] **Step 4: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "refactor: remove ObservabilityHook and clean up all Hook references"
```

---

## Task 11: 最终验证

- [ ] **Step 1: 完整编译**

Run: `mvn clean compile`
Expected: BUILD SUCCESS，无废弃 API 警告（除 RAG/LTM）

- [ ] **Step 2: 检查残留的废弃 import**

Run: `grep -rn "io.agentscope.core.hook\|io.agentscope.core.memory\b\|SkillBox\|structuredOutputReminder\|io.agentscope.core.agent.Event\b" src/main/java/ --include="*.java"`

Expected: 无结果（或仅有 RAG/LTM 相关的已知保留项）

- [ ] **Step 3: 启动应用验证**

Run: `DASHSCOPE_API_KEY=test mvn spring-boot:run`
Expected: 应用启动成功，无启动错误

- [ ] **Step 4: 提交最终状态**

如果有任何遗漏修复:
```bash
git add -A
git commit -m "fix: final cleanup for Phase 1 deprecated API migration"
```

---

## 风险与注意事项

1. **AgentEvent API 不确定性**: `streamEvents()` 返回的 `AgentEvent` 子类在 RC3 中可能与本文档描述不同。Task 6 需要根据实际 API 调整。
2. **MiddlewareBase 方法签名**: `onAgent`/`onReasoning`/`onActing` 等方法的具体签名和参数类型需要查看 RC3 源码确认。
3. **ApprovalMiddleware 停止机制**: Hook 通过 `stopAgent()` 停止 agent，Middleware 可能有不同的停止方式（如返回特定 Mono）。
4. **HarnessAgent.streamEvents() gap**: 子 agent 事件转发暂不可用，影响多 Agent 模式（Phase 2 需关注）。

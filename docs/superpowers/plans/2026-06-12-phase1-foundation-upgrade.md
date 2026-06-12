# AgentScope 2.0 Phase 1: 基础升级 & 废弃清理 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 升级到 AgentScope 2.0.0-RC3，清理所有 Part B 废弃 API（除 RAG/LTM 暂不迁移），确保编译通过且现有功能正常。

**Architecture:** 分两批执行。先做简单清理（版本升级、删除废弃方法、SkillBox 迁移、structuredOutputReminder 移除），再做核心迁移（Hook→EventSink + stream→streamEvents）。核心思路：自动生命周期事件改用 `agent.streamEvents()` 的 `Flux<AgentEvent>` 获取；手动多 Agent 事件提取为独立 `EventSink` 工具类。

**Tech Stack:** AgentScope 2.0.0-RC3, Spring Boot 3.5, Java 17, Project Reactor

---

## File Structure

**Create:**
- `src/main/java/com/skloda/agentscope/runtime/EventSink.java` — 轻量事件总线，替代 ObservabilityHook 的手动 emit 机制

**Modify:**
- `pom.xml` — 版本升级
- `src/main/java/com/skloda/agentscope/agent/AgentFactory.java` — 移除 SkillBox/structuredOutputReminder，改用 SkillRepository
- `src/main/java/com/skloda/agentscope/runtime/AgentRuntimeFactory.java` — 删除 @Deprecated Memory 方法
- `src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java` — 删除 @Deprecated Memory 方法，hooks 不再注册到 builder
- `src/main/java/com/skloda/agentscope/hook/ObservabilityHook.java` — 重写为 EventSink 桥接（不再实现 Hook）
- `src/main/java/com/skloda/agentscope/runtime/AgentRuntime.java` — 核心：stream() → streamEvents()
- `src/main/java/com/skloda/agentscope/runtime/StructuredOutputAgentRuntime.java` — 同步迁移
- `src/main/java/com/skloda/agentscope/runtime/StateGraphRuntime.java` — 同步迁移

---

## Task 1: 版本升级 & 编译验证

**Files:**
- Modify: `pom.xml:25`

- [ ] **Step 1: 升 pom.xml 版本号**

将 `agentscope.version` 从 `2.0.0-RC1` 改为 `2.0.0-RC3`：

```xml
<agentscope.version>2.0.0-RC3</agentscope.version>
```

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

如果编译失败，检查 RC1→RC3 之间的 breaking change，逐一修复。

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "chore: upgrade AgentScope from 2.0.0-RC1 to 2.0.0-RC3"
```

---

## Task 2: 删除 structuredOutputReminder 调用

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentFactory.java`

官方 A.1: `.structuredOutputReminder()` 已删除，模型层原生支持。

- [ ] **Step 1: 删除 buildAgent 中的 structuredOutputReminder 代码块**

在 `buildAgent()` 方法中（约 line 139-146），删除：

```java
if (config.getStructuredOutputClass() != null && !config.getStructuredOutputClass().isBlank()) {
    StructuredOutputReminder reminder = "PROMPT".equalsIgnoreCase(config.getStructuredOutputReminder())
            ? StructuredOutputReminder.PROMPT
            : StructuredOutputReminder.TOOL_CHOICE;
    builder.structuredOutputReminder(reminder);
    log.info("  Configured structured output for agent: {} (class={}, mode={})",
            agentId, config.getStructuredOutputClass(), reminder);
}
```

- [ ] **Step 2: 删除 buildAgentWithPermission 中的同样代码块**

在 `buildAgentWithPermission()` 方法中（约 line 223-230），删除相同的代码块。

- [ ] **Step 3: 删除未使用的 import**

删除 `import io.agentscope.core.model.StructuredOutputReminder;`

- [ ] **Step 4: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/AgentFactory.java
git commit -m "refactor: remove deprecated structuredOutputReminder (model-native in 2.0)"
```

---

## Task 3: SkillBox → SkillRepository 迁移

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentFactory.java`

官方 B.1: `SkillBox` → `AgentSkillRepository`，使用 `.skillRepository()` 替代 `.skillBox()`。

- [ ] **Step 1: 重写 registerToolsAndSkills 方法**

替换 `AgentFactory.java` 的 `registerToolsAndSkills()` 方法（line 289-335）。新实现使用 `ClasspathSkillRepository` + `builder.skillRepository()`：

```java
private void registerToolsAndSkills(ReActAgent.Builder builder, Toolkit toolkit,
                                     AgentConfig config, String agentId) {
    Set<String> skillToolNames = new HashSet<>();

    if (!config.getSkills().isEmpty()) {
        try (ClasspathSkillRepository repo = new ClasspathSkillRepository("skills")) {
            builder.skillRepository(repo);
            for (String skillName : config.getSkills()) {
                if (toolRegistry.hasTool(skillName)) {
                    repo.getSkill(skillName);
                    toolkit.registerTool(toolRegistry.getTool(skillName));
                    skillToolNames.addAll(toolRegistry.getToolNamesForClass(skillName));
                    log.info("  Registered skill: {} for agent: {}", skillName, agentId);
                } else {
                    log.error("  Tool for skill not found in registry: {} (agent: {})", skillName, agentId);
                }
            }
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

- [ ] **Step 2: 删除未使用的 import**

删除 `import io.agentscope.core.skill.SkillBox;`

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/AgentFactory.java
git commit -m "refactor: migrate SkillBox to SkillRepository (AgentScope 2.0)"
```

---

## Task 4: 删除 @Deprecated Memory 方法

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/runtime/AgentRuntimeFactory.java`
- Modify: `src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java`

- [ ] **Step 1: AgentRuntimeFactory — 删除所有 *WithMemory 方法**

删除以下方法：
1. `createRuntimeWithMemory(String, Memory)` — 整个方法
2. `createRoutingRuntimeWithMemory(String, Memory)` — 整个方法
3. `createHandoffsRuntimeWithMemory(String, Memory)` — 整个方法
4. `createStateGraphRuntimeWithMemory(String, Memory)` — 整个方法
5. `createHarnessRuntimeWithMemory(String, Memory)` — 整个方法
6. `createSingleRuntimeWithMemory(String, Memory)` — 整个方法
7. 删除 `import io.agentscope.core.memory.Memory;`

- [ ] **Step 2: CompositeAgentFactory — 删除 @Deprecated Memory 重载**

删除以下方法：
1. `createSingleAgentForSession(String, Memory, Hook...)` (line 111-114)
2. `createSingleAgentForSession(String, Memory, Hook, ApprovalHook)` (line 119-122)
3. `createStateGraphAgent(AgentConfig, Memory)` (line 160-163)
4. `createRoutingAgent(AgentConfig, Memory, Hook...)` (line 257-259)
5. `createHandoffsAgent(AgentConfig, Memory, Hook...)` (line 386-389)

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/skloda/agentscope/runtime/AgentRuntimeFactory.java src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java
git commit -m "refactor: remove deprecated Memory-based methods (use Session API)"
```

---

## Task 5: 创建 EventSink 工具类

**Files:**
- Create: `src/main/java/com/skloda/agentscope/runtime/EventSink.java`

ObservabilityHook 有两类功能：(1) Hook 自动捕获生命周期事件，(2) 手动 emit 多 Agent 事件。迁移后 (1) 由 streamEvents() 处理，(2) 由 EventSink 处理。

- [ ] **Step 1: 创建 EventSink 类**

```java
package com.skloda.agentscope.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Lightweight event bus for manually emitting multi-agent SSE events.
 * Replaces the manual emit() functionality previously in ObservabilityHook.
 */
public class EventSink {

    private static final Logger log = LoggerFactory.getLogger(EventSink.class);

    private final Sinks.Many<Map<String, Object>> sink = Sinks.many().multicast().onBackpressureBuffer();

    public Flux<Map<String, Object>> asFlux() {
        return sink.asFlux();
    }

    public void emit(String type, Map<String, Object> data) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>(data);
        payload.put("type", type);
        log.debug("[EventSink] {}: {}", type, data);
        Sinks.EmitResult result = sink.tryEmitNext(payload);
        if (result.isFailure()) {
            log.warn("Failed to emit event {}: {}", type, result);
        }
    }

    public void complete() {
        sink.tryEmitComplete();
    }

    public static final String PIPELINE_START = "pipeline_start";
    public static final String PIPELINE_STEP_START = "pipeline_step_start";
    public static final String PIPELINE_STEP_END = "pipeline_step_end";
    public static final String PIPELINE_END = "pipeline_end";
    public static final String ROUTING_DECISION = "routing_decision";
    public static final String ROUTING_END = "routing_end";
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

    public void emitRoutingEnd(String routingId, String selectedAgent) {
        emit(ROUTING_END, Map.of("routingId", routingId, "selectedAgent", selectedAgent, "timestamp", System.currentTimeMillis()));
    }

    public void emitHandoffStart(String fromAgent, String toAgent, String reason) {
        emit(HANDOFF_START, Map.of("fromAgent", fromAgent, "toAgent", toAgent, "reason", reason, "timestamp", System.currentTimeMillis()));
    }

    public void emitHandoffComplete(String fromAgent, String toAgent) {
        emit(HANDOFF_COMPLETE, Map.of("fromAgent", fromAgent, "toAgent", toAgent, "timestamp", System.currentTimeMillis()));
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

    public void emitGraphTransition(String fromState, String toState, String trigger) {
        emit(GRAPH_TRANSITION, Map.of("fromState", fromState, "toState", toState, "trigger", trigger, "timestamp", System.currentTimeMillis()));
    }

    public void emitGraphAgentCall(String state, String agent) {
        emit(GRAPH_AGENT_CALL, Map.of("state", state, "agent", agent, "timestamp", System.currentTimeMillis()));
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

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/skloda/agentscope/runtime/EventSink.java
git commit -m "feat: add EventSink utility for multi-agent SSE events"
```

---

## Task 6: 重写 ObservabilityHook 为 EventSink 桥接

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/hook/ObservabilityHook.java`

核心变更：ObservabilityHook 不再实现 `Hook` 接口。自动生命周期事件由 Task 7 的 `streamEvents()` 处理，手动多 Agent 事件委托给 `EventSink`。

- [ ] **Step 1: 重写 ObservabilityHook**

完全替换 `ObservabilityHook.java` 内容：

```java
package com.skloda.agentscope.hook;

import com.skloda.agentscope.runtime.EventSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Bridge between legacy hook consumers and the new EventSink.
 * Automatic lifecycle events are now handled via agent.streamEvents() + AgentEvent processing.
 * Manual multi-agent events are delegated to EventSink.
 */
public class ObservabilityHook {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityHook.class);

    private final EventSink eventSink;

    public ObservabilityHook() {
        this.eventSink = new EventSink();
    }

    public EventSink getEventSink() {
        return eventSink;
    }

    public void addConsumer(BiConsumer<String, Map<String, Object>> consumer) {
        eventSink.asFlux().subscribe(payload -> {
            String type = (String) payload.get("type");
            consumer.accept(type, payload);
        });
    }

    public void removeConsumer(BiConsumer<String, Map<String, Object>> consumer) {
        // Flux subscription is passive; no explicit removal needed.
    }

    public void reset() {
        // No mutable state to reset
    }

    public void emitPipelineStart(String pipelineId, List<String> subAgents) { eventSink.emitPipelineStart(pipelineId, subAgents); }
    public void emitPipelineStepStart(String pipelineId, int stepIndex, String agentId) { eventSink.emitPipelineStepStart(pipelineId, stepIndex, agentId); }
    public void emitPipelineStepEnd(String pipelineId, int stepIndex, String agentId, long durationMs) { eventSink.emitPipelineStepEnd(pipelineId, stepIndex, agentId, durationMs); }
    public void emitPipelineEnd(String pipelineId, int totalSteps, long totalDurationMs) { eventSink.emitPipelineEnd(pipelineId, totalSteps, totalDurationMs); }
    public void emitRoutingDecision(String routingId, String selectedAgent, String reasoning) { eventSink.emitRoutingDecision(routingId, selectedAgent, reasoning); }
    public void emitRoutingEnd(String routingId, String selectedAgent) { eventSink.emitRoutingEnd(routingId, selectedAgent); }
    public void emitHandoffStart(String fromAgent, String toAgent, String reason) { eventSink.emitHandoffStart(fromAgent, toAgent, reason); }
    public void emitHandoffComplete(String fromAgent, String toAgent) { eventSink.emitHandoffComplete(fromAgent, toAgent); }
    public void emitLoopStart(int iteration) { eventSink.emitLoopStart(iteration); }
    public void emitLoopEnd(int totalIterations, boolean finalApproved) { eventSink.emitLoopEnd(totalIterations, finalApproved); }
    public void emitLoopIterationResult(int iteration, boolean approved, String feedback) { eventSink.emitLoopIterationResult(iteration, approved, feedback); }
    public void emitGraphTransition(String fromState, String toState, String trigger) { eventSink.emitGraphTransition(fromState, toState, trigger); }
    public void emitGraphAgentCall(String state, String agent) { eventSink.emitGraphAgentCall(state, agent); }
    public void emitRoundtableStart(String pipelineId, List<String> participants, int rounds) { eventSink.emitRoundtableStart(pipelineId, participants, rounds); }
    public void emitRoundStart(int round) { eventSink.emitRoundStart(round); }
    public void emitRoundEnd(int round) { eventSink.emitRoundEnd(round); }
    public void emitRoundMessage(String agentId, String content) { eventSink.emitRoundMessage(agentId, content); }
    public void emitRoundtableSummary(String agentId, String content) { eventSink.emitRoundtableSummary(agentId, content); }
    public void emitTaskDelegate(String from, String to, String task) { eventSink.emitTaskDelegate(from, to, task); }
    public void emitTaskStart(String agent) { eventSink.emitTaskStart(agent); }
    public void emitTaskEnd(String agent, String outputPreview) { eventSink.emitTaskEnd(agent, outputPreview); }
    public void emitTaskAggregate(int totalTasks) { eventSink.emitTaskAggregate(totalTasks); }
}
```

- [ ] **Step 2: AgentFactory — hooks 不再注册到 builder**

在 `AgentFactory.java` 的 `buildAgent()` 和 `buildAgentWithPermission()` 方法中，删除 `builder.hooks(List.of(hooks))` 调用（约 line 173-176 和 254-257）。

同时删除 `import io.agentscope.core.hook.Hook;`

- [ ] **Step 3: CompositeAgentFactory — hooks 不再注册到 builder**

在 `createRoutingAgent()` 和 `createHandoffsAgent()` 方法中，删除 `builder.hooks(List.of(hooks))` 调用（约 line 247-249 和 376-378）。

同时删除 `import io.agentscope.core.hook.Hook;`（如果不再有其他引用）

- [ ] **Step 4: 编译验证**

Run: `mvn clean compile -q`
Expected: 编译失败 — AgentRuntime 等消费者尚未更新。记录错误用于 Task 7。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/hook/ObservabilityHook.java src/main/java/com/skloda/agentscope/agent/AgentFactory.java src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java
git commit -m "refactor: rewrite ObservabilityHook as EventSink bridge, remove Hook registration from builders"
```

---

## Task 7: 核心迁移 — AgentRuntime stream() → streamEvents()

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/runtime/AgentRuntime.java`

这是 Phase 1 最大的改动。

**实现前必须先确认：** 检查 `io.agentscope.core.event.AgentEventType` 的枚举值和 `io.agentscope.core.event.*` 事件子类的确切名称。运行以下命令查看实际 API：

```bash
mvn dependency:sources -q && find ~/.m2/repository/io/agentscope/agentscope-core/2.0.0-RC3 -name "*.jar" | head -1 | xargs jar tf | grep "event" | head -30
```

如果 `AgentEventType` 枚举值与下方代码不同，需相应调整 `mapAgentEvent` 的 case 分支。

- [ ] **Step 1: 确认 AgentEvent API**

运行上述命令，记录 `AgentEventType` 枚举值和事件子类名称。据此调整 Step 2-3 的代码。

- [ ] **Step 2: 重写 AgentRuntime**

完全替换 `AgentRuntime.java`。核心变更：
1. `agent.stream()` → `agent.streamEvents()`
2. 事件从 `AgentEvent` 类型化流获取，不再从 Hook
3. 手动多 Agent 事件从 `hook.getEventSink().asFlux()` 获取
4. 删除 `Sinks.Many sink` 字段、`hookBridge`、`emit()`、`addTemporaryHook`、`StopAfterApprovedToolHook`
5. ApprovalHook 的 HITL 逻辑保持不变（使用 `agent.call()` 恢复）

```java
package com.skloda.agentscope.runtime;

import com.skloda.agentscope.hook.ApprovalHook;
import com.skloda.agentscope.hook.ObservabilityHook;
import com.skloda.agentscope.service.ApprovalService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.message.*;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime container for a single Agent interaction session.
 * Uses agent.streamEvents() for lifecycle events and EventSink for manual multi-agent events.
 */
public class AgentRuntime implements StreamingAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    @Getter
    private final ReActAgent agent;
    @Getter
    private final ObservabilityHook hook;
    private final ApprovalHook approvalHook;
    private final ApprovalService approvalService;
    private final String agentId;
    private final String sessionId;
    private final Runnable onClose;

    public AgentRuntime(ReActAgent agent, ObservabilityHook hook) {
        this(agent, hook, null, null, null, null, null);
    }

    public AgentRuntime(ReActAgent agent, ObservabilityHook hook, ApprovalHook approvalHook,
                        ApprovalService approvalService, String agentId) {
        this(agent, hook, approvalHook, approvalService, agentId, null, null);
    }

    public AgentRuntime(ReActAgent agent, ObservabilityHook hook, ApprovalHook approvalHook,
                        ApprovalService approvalService, String agentId, String sessionId,
                        Runnable onClose) {
        this.agent = agent;
        this.hook = hook;
        this.approvalHook = approvalHook;
        this.approvalService = approvalService;
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

        Flux<Map<String, Object>> sinkEvents = hook.getEventSink().asFlux();

        Flux<Map<String, Object>> agentEvents;
        if (isApprovalResume) {
            agentEvents = Flux.create(fluxSink -> {
                reactor.core.publisher.Mono<Msg> resumeCall = userMsg != null
                        ? agent.call(userMsg)
                        : agent.call();
                resumeCall.subscribe(
                        msg -> emitResponseText(msg, fluxSink),
                        error -> {
                            log.error("Resume stream error", error);
                            fluxSink.error(error);
                        },
                        () -> completeStream(fluxSink, false)
                );
            });
        } else {
            agentEvents = agent.streamEvents(userMsg)
                    .flatMap(this::mapAgentEvent)
                    .doOnNext(event -> log.debug("[streamEvents] {}", event));
        }

        return Flux.merge(sinkEvents, agentEvents)
                .doOnCancel(this::close)
                .doOnComplete(() -> {
                    hook.getEventSink().complete();
                    this.close();
                })
                .doOnError(e -> {
                    log.error("Stream error for agent: {}", agent.getName(), e);
                    hook.getEventSink().complete();
                    this.close();
                });
    }

    private Flux<Map<String, Object>> mapAgentEvent(AgentEvent event) {
        // NOTE: Enum values must be verified against AgentScope 2.0.0-RC3 AgentEventType.
        // Common values: TEXT_BLOCK_DELTA, AGENT_START, AGENT_END, TOOL_CALL_START, TOOL_CALL_END
        Map<String, Object> mapped = switch (event.getType().name()) {
            case "TEXT_BLOCK_DELTA" -> {
                if (event instanceof io.agentscope.core.event.TextBlockDeltaEvent e) {
                    yield Map.of("type", "text", "content", (Object) e.getDelta());
                }
                yield null;
            }
            case "AGENT_START" -> Map.of("type", "agent_start", "agentName",
                    (Object) (event.getAgentName() != null ? event.getAgentName() : ""),
                    "timestamp", System.currentTimeMillis());
            case "AGENT_END" -> Map.of("type", "agent_end", "agentName",
                    (Object) (event.getAgentName() != null ? event.getAgentName() : ""),
                    "timestamp", System.currentTimeMillis());
            case "TOOL_CALL_START" -> {
                String name = "";
                // ToolCallStartEvent may carry toolCallName — check actual API
                try {
                    var method = event.getClass().getMethod("getToolCallName");
                    name = (String) method.invoke(event);
                } catch (Exception ignored) {}
                yield Map.of("type", "tool_start", "name", (Object) (name != null ? name : ""),
                        "timestamp", System.currentTimeMillis());
            }
            case "TOOL_CALL_END" -> {
                String name = "";
                try {
                    var method = event.getClass().getMethod("getToolCallName");
                    name = (String) method.invoke(event);
                } catch (Exception ignored) {}
                yield Map.of("type", "tool_end", "name", (Object) (name != null ? name : ""),
                        "timestamp", System.currentTimeMillis());
            }
            default -> null;
        };

        if (mapped == null) return Flux.empty();
        return Flux.just(mapped);
    }

    private void emitResponseText(Msg msg, reactor.core.publisher.FluxSink<Map<String, Object>> fluxSink) {
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

    private void completeStream(reactor.core.publisher.FluxSink<Map<String, Object>> fluxSink,
                                boolean skipClose) {
        log.debug("Stream completing for agent: {}", agent.getName());

        if (!skipClose && approvalHook != null && approvalHook.isApprovalTriggered()) {
            String approvalId = approvalService.registerPendingApproval(
                    agent, hook, approvalHook.getPendingToolUseBlocks(), agentId, sessionId);

            fluxSink.next(Map.of(
                    "type", "pending_approval",
                    "approvalId", approvalId,
                    "agentId", agentId != null ? agentId : "",
                    "toolCalls", approvalHook.getPendingToolCallsForSse(),
                    "timestamp", System.currentTimeMillis()
            ));
            fluxSink.next(Map.of("type", "done"));
            fluxSink.complete();
            return;
        }

        fluxSink.next(Map.of("type", "done"));
        fluxSink.complete();
    }

    @Override
    public void close() {
        hook.getEventSink().complete();
        if (onClose != null) {
            onClose.run();
        }
        log.debug("AgentRuntime closed for agent: {}", agent.getName());
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS（可能需要根据实际 API 微调事件类型名）

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/skloda/agentscope/runtime/AgentRuntime.java
git commit -m "refactor: migrate AgentRuntime from stream()+Hook to streamEvents()+AgentEvent"
```

---

## Task 8: 同步迁移 StructuredOutputAgentRuntime

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/runtime/StructuredOutputAgentRuntime.java`

- [ ] **Step 1: 删除旧的 sink/hookBridge/emit 机制**

删除 `Sinks.Many<Map<String, Object>> sink`、`BiConsumer<String, Map<String, Object>> hookBridge`、`emit()` 方法。

- [ ] **Step 2: 更新构造函数**

```java
StructuredOutputAgentRuntime(ReActAgent agent, ObservabilityHook hook,
                             String structuredOutputClassName,
                             StructuredOutputValidator validator,
                             int maxRepairAttempts) {
    this.agent = agent;
    this.hook = hook;
    this.structuredOutputClassName = structuredOutputClassName;
    this.validator = validator;
    this.maxRepairAttempts = maxRepairAttempts;
}
```

- [ ] **Step 3: 更新 stream() 中 hookEvents 改为 sinkEvents**

```java
Flux<Map<String, Object>> sinkEvents = hook.getEventSink().asFlux();
// ... (call + validation 逻辑保持不变)
return Flux.merge(sinkEvents, resultStream).doOnCancel(this::close);
```

- [ ] **Step 4: 更新 close()**

```java
@Override
public void close() {
    hook.getEventSink().complete();
    log.debug("StructuredOutputAgentRuntime closed for agent: {}", agent.getName());
}
```

- [ ] **Step 5: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/skloda/agentscope/runtime/StructuredOutputAgentRuntime.java
git commit -m "refactor: migrate StructuredOutputAgentRuntime to EventSink"
```

---

## Task 9: 同步迁移 StateGraphRuntime

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/runtime/StateGraphRuntime.java`

- [ ] **Step 1: 读取当前 StateGraphRuntime.java**

Run: `cat src/main/java/com/skloda/agentscope/runtime/StateGraphRuntime.java`

根据文件内容，更新 ObservabilityHook 引用以匹配新 API（`hook.getEventSink()` 模式）。

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/skloda/agentscope/runtime/StateGraphRuntime.java
git commit -m "refactor: migrate StateGraphRuntime to EventSink"
```

---

## Task 10: 最终验证

- [ ] **Step 1: 全量编译**

Run: `mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 2: 检查废弃 API 警告**

Run: `mvn clean compile 2>&1 | grep -i "deprecated" | grep -i agentscope`

Expected: 仅剩 RAG（B.5 `.knowledge()/.ragMode()/.retrieveConfig()`）和 LTM（B.6 `.longTermMemory()/.longTermMemoryMode()`）相关警告。

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: Phase 1 complete — AgentScope 2.0 RC3 foundation upgrade"
```

---

## 实现注意事项

1. **AgentEventType 枚举值**: Task 7 中 `mapAgentEvent()` 使用的 `TEXT_BLOCK_DELTA`, `AGENT_START`, `AGENT_END`, `TOOL_CALL_START`, `TOOL_CALL_END` 等枚举值需在实现时根据 RC3 实际 API 确认。使用 `event.getType().name()` 字符串匹配比直接 switch 枚举更安全。

2. **Event 子类名称**: `TextBlockDeltaEvent`, `ToolCallStartEvent`, `ToolCallEndEvent` 等需确认包路径（应在 `io.agentscope.core.event.*`）。Task 7 Step 1 的验证命令必须在写代码前执行。

3. **ApprovalHook 暂不迁移**: ApprovalHook 仍实现 Hook 接口。它的 `stopAgent()` 在 2.0 streamEvents() 中可能有新实现方式（如 `RequireUserConfirmEvent`），Phase 1 保持现状。

4. **RAG/LTM 保持现状**: 官方标注 v2 替代方案"推进中"，v1 API 仍可调用。

5. **`Event`/`EventType`/`EventSource` 软弃用**: CompositeAgentFactory 中 `StreamOptions`/`EventType` 引用（用于 SubAgentTool 的 forwardEvents）暂时保留，等官方统一切换后再清理。

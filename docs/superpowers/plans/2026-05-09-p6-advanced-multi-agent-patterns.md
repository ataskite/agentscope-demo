# P6: Advanced Multi-Agent Patterns Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 5 advanced multi-agent patterns (Loop Pipeline, StateGraph, MsgHub, Subagents Sequential, Subagents Parallel) to the AgentScope demo.

**Architecture:** Extend the existing `CompositeAgentFactory` + `AgentRuntimeFactory` pattern. Each new pattern gets its own Pipeline class under `composite/pipeline/` (or `composite/graph/` for StateGraph), keeping the factory lean. New `AgentType` enum values route to appropriate runtime types. All patterns emit structured SSE events for the debug panel.

**Tech Stack:** Java 17, Spring Boot 3.5, AgentScope 1.0.12, Project Reactor, YAML config, vanilla JS frontend.

**Spec:** `docs/superpowers/specs/2026-05-09-p6-advanced-multi-agent-patterns-design.md`

---

## File Map

### New files to create

| File | Responsibility |
|------|---------------|
| `agent/LoopConfig.java` | Loop config POJO (maxIterations, exitCondition) |
| `agent/StateConfig.java` | StateGraph state definition (name, agent, transitions) |
| `agent/StateTransition.java` | Single state transition (event/condition, target) |
| `agent/MsgHubConfig.java` | MsgHub config POJO (rounds, summaryRole) |
| `composite/pipeline/LoopPipeline.java` | Write-review-revise loop pipeline implementing `Pipeline<Msg>` |
| `composite/pipeline/RoundTablePipeline.java` | Expert roundtable via MsgHub implementing `Pipeline<Msg>` |
| `composite/pipeline/TaskOrchestratorPipeline.java` | Sequential task handoff pipeline implementing `Pipeline<Msg>` |
| `composite/pipeline/TaskDispatcherPipeline.java` | Parallel task delegation pipeline implementing `Pipeline<Msg>` |
| `composite/graph/OrderFulfillmentGraph.java` | StateGraph-based order fulfillment (custom state machine) |
| `runtime/MsgHubRuntime.java` | Runtime for MsgHub group conversations |

### Existing files to modify

| File | Change |
|------|--------|
| `agent/AgentType.java` | Add 5 new enum values |
| `agent/AgentConfig.java` | Add loopConfig, states, msgHubConfig fields |
| `agent/SubAgentConfig.java` | Add role, taskTemplate fields |
| `composite/CompositeAgentFactory.java` | Add 5 new factory methods |
| `runtime/AgentRuntimeFactory.java` | Add 5 new runtime creation methods |
| `runtime/PipelineAgentRuntime.java` | Support LOOP/SUBAGENT pipelines |
| `hook/ObservabilityHook.java` | Add new event type constants + emitter methods |
| `resources/config/agents.yml` | Add 5 new agent configurations |
| `static/scripts/modules/debug.js` | Add new event handlers |
| `static/scripts/chat.js` | Wire new SSE event types |

---

## Task 1: Config Model — New Types and AgentConfig Fields

**Files:**
- Create: `src/main/java/com/skloda/agentscope/agent/LoopConfig.java`
- Create: `src/main/java/com/skloda/agentscope/agent/StateConfig.java`
- Create: `src/main/java/com/skloda/agentscope/agent/StateTransition.java`
- Create: `src/main/java/com/skloda/agentscope/agent/MsgHubConfig.java`
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentType.java:3-9`
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentConfig.java:56-60`
- Modify: `src/main/java/com/skloda/agentscope/agent/SubAgentConfig.java:13-15`

- [ ] **Step 1: Add new enum values to AgentType**

In `AgentType.java`, add 5 new values after `DEBATE`:

```java
public enum AgentType {
    SINGLE(true),
    SEQUENTIAL(false),
    PARALLEL(false),
    ROUTING(false),
    HANDOFFS(false),
    DEBATE(false),
    LOOP(false),
    STATE_GRAPH(false),
    MSG_HUB(false),
    SUBAGENT_SEQ(false),
    SUBAGENT_PAR(false);
    // ... rest unchanged
}
```

- [ ] **Step 2: Create LoopConfig.java**

```java
package com.skloda.agentscope.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoopConfig {
    private int maxIterations = 3;
    private String exitCondition = "AUTO"; // AUTO or FIXED
}
```

- [ ] **Step 3: Create StateTransition.java**

```java
package com.skloda.agentscope.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StateTransition {
    private String event;      // for deterministic transitions (user-triggered)
    private String condition;  // for agent-decided transitions (approved/rejected)
    private String target;     // target state name
}
```

- [ ] **Step 4: Create StateConfig.java**

```java
package com.skloda.agentscope.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StateConfig {
    private String name;
    private String agent; // agentId for agent-decided states, null for deterministic
    private List<StateTransition> transitions;
}
```

- [ ] **Step 5: Create MsgHubConfig.java**

```java
package com.skloda.agentscope.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MsgHubConfig {
    private int rounds = 3;
    private String summaryRole = "MODERATOR";
}
```

- [ ] **Step 6: Add fields to AgentConfig.java**

After the existing multi-agent fields (line ~60), add:

```java
    // === P6 Advanced multi-agent fields ===
    private LoopConfig loopConfig;
    private List<StateConfig> states = new ArrayList<>();
    private MsgHubConfig msgHubConfig;
```

- [ ] **Step 7: Add fields to SubAgentConfig.java**

Add `role` and `taskTemplate` fields:

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubAgentConfig {
    private String agentId;
    private String description;
    private String role;           // WRITER, CRITIC, MODERATOR, EXPERT, RESEARCHER, etc.
    private String taskTemplate;   // template with {input} and {prevOutput} variables
}
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/LoopConfig.java \
        src/main/java/com/skloda/agentscope/agent/StateConfig.java \
        src/main/java/com/skloda/agentscope/agent/StateTransition.java \
        src/main/java/com/skloda/agentscope/agent/MsgHubConfig.java \
        src/main/java/com/skloda/agentscope/agent/AgentType.java \
        src/main/java/com/skloda/agentscope/agent/AgentConfig.java \
        src/main/java/com/skloda/agentscope/agent/SubAgentConfig.java
git commit -m "feat: add P6 config model — AgentType enums, LoopConfig, StateConfig, MsgHubConfig"
```

---

## Task 2: ObservabilityHook — New Event Constants and Emitters

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/hook/ObservabilityHook.java:38-48` (constants)
- Modify: `src/main/java/com/skloda/agentscope/hook/ObservabilityHook.java:419` (add emitters after existing)

- [ ] **Step 1: Add new event type constants**

After line 48 (`HANDOFF_ERROR`), add:

```java
    // P6 advanced pattern events
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
```

- [ ] **Step 2: Add emitter methods for Loop events**

After the existing `emitHandoffComplete` method (~line 418), add:

```java
    // ---- P6 Loop events ----

    public void emitLoopStart(int iteration) {
        emit(LOOP_START, Map.of(
                "iteration", iteration,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitLoopEnd(int totalIterations, boolean finalApproved) {
        emit(LOOP_END, Map.of(
                "totalIterations", totalIterations,
                "finalApproved", finalApproved,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitLoopIterationResult(int iteration, boolean approved, String feedback) {
        emit(LOOP_ITERATION_RESULT, Map.of(
                "iteration", iteration,
                "approved", approved,
                "feedback", feedback,
                "timestamp", System.currentTimeMillis()
        ));
    }

    // ---- P6 StateGraph events ----

    public void emitGraphTransition(String fromState, String toState, String trigger) {
        emit(GRAPH_TRANSITION, Map.of(
                "fromState", fromState,
                "toState", toState,
                "trigger", trigger,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitGraphAgentCall(String state, String agent) {
        emit(GRAPH_AGENT_CALL, Map.of(
                "state", state,
                "agent", agent,
                "timestamp", System.currentTimeMillis()
        ));
    }

    // ---- P6 MsgHub events ----

    public void emitRoundtableStart(String pipelineId, List<String> participants, int rounds) {
        emit(ROUNDTABLE_START, Map.of(
                "pipelineId", pipelineId,
                "participants", participants,
                "rounds", rounds,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitRoundStart(int round) {
        emit(ROUND_START, Map.of(
                "round", round,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitRoundEnd(int round) {
        emit(ROUND_END, Map.of(
                "round", round,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitRoundMessage(String agentId, String content) {
        emit(ROUND_MESSAGE, Map.of(
                "agent", agentId,
                "content", content,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitRoundtableSummary(String agentId, String content) {
        emit(ROUNDTABLE_SUMMARY, Map.of(
                "agent", agentId,
                "content", content,
                "timestamp", System.currentTimeMillis()
        ));
    }

    // ---- P6 Subagent events ----

    public void emitTaskDelegate(String from, String to, String task) {
        emit(TASK_DELEGATE, Map.of(
                "from", from,
                "to", to,
                "task", task,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitTaskStart(String agent) {
        emit(TASK_START, Map.of(
                "agent", agent,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitTaskEnd(String agent, String outputPreview) {
        emit(TASK_END, Map.of(
                "agent", agent,
                "outputPreview", outputPreview,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitTaskAggregate(int totalTasks) {
        emit(TASK_AGGREGATE, Map.of(
                "totalTasks", totalTasks,
                "timestamp", System.currentTimeMillis()
        ));
    }
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/skloda/agentscope/hook/ObservabilityHook.java
git commit -m "feat: add P6 event constants and emitter methods to ObservabilityHook"
```

---

## Task 3: Loop Pipeline — Implementation

**Files:**
- Create: `src/main/java/com/skloda/agentscope/composite/pipeline/LoopPipeline.java`

- [ ] **Step 1: Create LoopPipeline.java**

```java
package com.skloda.agentscope.composite.pipeline;

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.pipeline.Pipeline;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Loop pipeline implementing write-review-revise pattern.
 * Writer produces content, Critic reviews it. Loop until approved or max iterations reached.
 */
public class LoopPipeline implements Pipeline<Msg> {

    private final AgentBase writer;
    private final AgentBase critic;
    private final int maxIterations;
    private final boolean autoExit;
    private final List<BiConsumer<String, Map<String, Object>>> eventConsumers = new ArrayList<>();

    public LoopPipeline(AgentBase writer, AgentBase critic, int maxIterations, boolean autoExit) {
        this.writer = writer;
        this.critic = critic;
        this.maxIterations = maxIterations;
        this.autoExit = autoExit;
    }

    public void addEventConsumer(BiConsumer<String, Map<String, Object>> consumer) {
        eventConsumers.add(consumer);
    }

    @Override
    public Mono<Msg> execute(Msg input) {
        return execute(input, null);
    }

    @Override
    public Mono<Msg> execute(Msg input, Class<?> structuredOutputClass) {
        return loopIteration(input, 1, null);
    }

    private Mono<Msg> loopIteration(Msg currentInput, int iteration, String previousFeedback) {
        if (iteration > maxIterations) {
            // Return the last writer output as-is (already produced in previous iteration)
            return Mono.justOrEmpty(currentInput);
        }

        emit("loop_start", Map.of("iteration", iteration));

        // Build writer prompt: original input + previous feedback
        Msg writerMsg = buildWriterMessage(currentInput, previousFeedback, iteration == 1);
        return writer.call(writerMsg)
                .flatMap(writerOutput -> {
                    String writerText = extractText(writerOutput);
                    emit("pipeline_step_end", Map.of("agentId", "writer", "iteration", iteration));

                    // Critic reviews
                    Msg criticMsg = buildCriticMessage(writerText);
                    return critic.call(criticMsg)
                            .flatMap(criticOutput -> {
                                String criticText = extractText(criticOutput);
                                emit("pipeline_step_end", Map.of("agentId", "critic", "iteration", iteration));

                                boolean approved = autoExit && containsApproval(criticText);
                                emit("loop_iteration_result", Map.of(
                                        "iteration", iteration,
                                        "approved", approved,
                                        "feedback", truncate(criticText, 200)
                                ));

                                if (approved) {
                                    emit("loop_end", Map.of(
                                            "totalIterations", iteration,
                                            "finalApproved", true
                                    ));
                                    // Return final version: writer output with critic summary
                                    return Mono.just(buildFinalOutput(writerText, criticText));
                                } else if (iteration >= maxIterations) {
                                    emit("loop_end", Map.of(
                                            "totalIterations", iteration,
                                            "finalApproved", false
                                    ));
                                    return Mono.just(buildFinalOutput(writerText, criticText));
                                } else {
                                    // Continue loop with writer output as context
                                    return loopIteration(currentInput, iteration + 1, criticText);
                                }
                            });
                });
    }

    private Msg buildWriterMessage(Msg originalInput, String feedback, boolean isFirst) {
        StringBuilder sb = new StringBuilder();
        // Extract original request text
        if (originalInput != null && originalInput.getContent() != null) {
            for (ContentBlock block : originalInput.getContent()) {
                if (block instanceof TextBlock tb) {
                    sb.append(tb.getText());
                }
            }
        }
        if (!isFirst && feedback != null) {
            sb.append("\n\n---\n\n## 评审意见\n\n").append(feedback);
            sb.append("\n\n请根据以上评审意见修改你的内容。保留原文的核心内容，针对评审中指出的问题进行改进。");
        }
        return Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(sb.toString()).build())
                .build();
    }

    private Msg buildCriticMessage(String writerOutput) {
        String prompt = "请评审以下内容。如果你认为内容已经足够好，请在回复中包含【通过】。\n" +
                "如果需要修改，请给出具体的修改建议。\n\n" +
                "---\n\n" + writerOutput;
        return Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(prompt).build())
                .build();
    }

    private boolean containsApproval(String text) {
        if (text == null) return false;
        return text.contains("通过") || text.contains("APPROVED") || text.contains("approved");
    }

    private Msg buildFinalOutput(String writerText, String criticText) {
        String finalContent = writerText + "\n\n---\n\n**评审总结:**\n" + truncate(criticText, 500);
        return Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text(finalContent).build())
                .build();
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private void emit(String type, Map<String, Object> data) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>(data);
        payload.put("type", type);
        for (var consumer : eventConsumers) {
            consumer.accept(type, payload);
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/skloda/agentscope/composite/pipeline/LoopPipeline.java
git commit -m "feat: add LoopPipeline for write-review-revise pattern"
```

---

## Task 4: StateGraph — Order Fulfillment

**Files:**
- Create: `src/main/java/com/skloda/agentscope/composite/graph/OrderFulfillmentGraph.java`

- [ ] **Step 1: Create OrderFulfillmentGraph.java**

Since AgentScope doesn't provide a built-in StateGraph, implement a custom state machine that wraps ReActAgent instances at decision states.

```java
package com.skloda.agentscope.composite.graph;

import com.skloda.agentscope.agent.StateConfig;
import com.skloda.agentscope.agent.StateTransition;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom state machine for order fulfillment workflow.
 * States with agents: LLM decides which transition to take.
 * States without agents: user input triggers deterministic transitions.
 */
public class OrderFulfillmentGraph {

    private final List<StateConfig> states;
    private final Map<String, ReActAgent> stateAgents;
    private final List<BiConsumer<String, Map<String, Object>>> eventConsumers = new ArrayList<>();
    private String currentState;

    public OrderFulfillmentGraph(List<StateConfig> states, Map<String, ReActAgent> stateAgents) {
        this.states = states;
        this.stateAgents = stateAgents;
        this.currentState = states.isEmpty() ? null : states.get(0).getName();
    }

    public void addEventConsumer(BiConsumer<String, Map<String, Object>> consumer) {
        eventConsumers.add(consumer);
    }

    /**
     * Process user input and advance the state machine.
     * Returns the final message when reaching a terminal state, or an intermediate response.
     */
    public Mono<Msg> execute(Msg userMsg) {
        return processState(userMsg, currentState);
    }

    private Mono<Msg> processState(Msg userMsg, String stateName) {
        StateConfig state = findState(stateName);
        if (state == null) {
            return Mono.just(buildTextMsg("未知状态: " + stateName));
        }

        if (state.getAgent() != null && stateAgents.containsKey(stateName)) {
            // Agent-decided state: call LLM to determine transition
            ReActAgent agent = stateAgents.get(stateName);
            emit("graph_agent_call", Map.of("state", stateName, "agent", state.getAgent()));

            Msg agentInput = buildAgentInput(stateName, userMsg);
            return agent.call(agentInput)
                    .flatMap(agentOutput -> {
                        String decision = extractDecision(agentOutput);
                        StateTransition matched = findMatchingTransition(state, decision);
                        if (matched == null) {
                            matched = state.getTransitions().get(0); // fallback to first transition
                        }

                        String fromState = stateName;
                        currentState = matched.getTarget();

                        emit("graph_transition", Map.of(
                                "fromState", fromState,
                                "toState", matched.getTarget(),
                                "trigger", matched.getCondition() != null ? matched.getCondition() : "agent_decision"
                        ));

                        String agentText = extractText(agentOutput);
                        // Check if next state is terminal
                        StateConfig nextState = findState(matched.getTarget());
                        if (nextState == null || nextState.getTransitions() == null || nextState.getTransitions().isEmpty()) {
                            // Terminal state
                            return Mono.just(buildTextMsg(agentText));
                        }
                        // Continue to next state
                        return processState(userMsg, matched.getTarget());
                    });
        } else {
            // Deterministic state: match user input to transition event
            String inputText = extractText(userMsg);
            StateTransition matched = findEventTransition(state, inputText);
            if (matched == null) {
                // No matching event, ask user for valid input
                return Mono.just(buildTextMsg(
                        "当前状态: " + stateName + "\n可用操作: " +
                        formatAvailableEvents(state)));
            }

            String fromState = stateName;
            currentState = matched.getTarget();

            emit("graph_transition", Map.of(
                    "fromState", fromState,
                    "toState", matched.getTarget(),
                    "trigger", matched.getEvent()
            ));

            StateConfig nextState = findState(matched.getTarget());
            if (nextState == null || nextState.getTransitions() == null || nextState.getTransitions().isEmpty()) {
                // Terminal state
                return Mono.just(buildTextMsg("流程已到达终态: " + matched.getTarget()));
            }
            return processState(userMsg, matched.getTarget());
        }
    }

    private Msg buildAgentInput(String stateName, Msg userMsg) {
        String context = "当前订单状态: " + stateName + "\n";
        String userInput = extractText(userMsg);
        context += "用户输入: " + userInput + "\n\n";
        context += "请根据订单状态和用户输入，做出决策。在回复中用【决策:xxx】格式表明你的决策。";
        return Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(context).build())
                .build();
    }

    private String extractDecision(Msg agentOutput) {
        String text = extractText(agentOutput);
        Pattern pattern = Pattern.compile("【决策:(.+?)】");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim().toLowerCase();
        }
        return text.toLowerCase();
    }

    private StateTransition findMatchingTransition(StateConfig state, String decision) {
        for (StateTransition t : state.getTransitions()) {
            if (t.getCondition() != null && decision.contains(t.getCondition().toLowerCase())) {
                return t;
            }
        }
        return null;
    }

    private StateTransition findEventTransition(StateConfig state, String input) {
        if (state.getTransitions() == null) return null;
        for (StateTransition t : state.getTransitions()) {
            if (t.getEvent() != null && input.toLowerCase().contains(t.getEvent().toLowerCase())) {
                return t;
            }
        }
        // Also match Chinese equivalents
        Map<String, String> eventAliases = Map.of(
                "submit", "提交",
                "review", "审核",
                "pay", "支付",
                "ship", "发货",
                "retry", "重试"
        );
        for (StateTransition t : state.getTransitions()) {
            if (t.getEvent() != null) {
                String alias = eventAliases.getOrDefault(t.getEvent(), "");
                if (!alias.isEmpty() && input.contains(alias)) {
                    return t;
                }
            }
        }
        return null;
    }

    private String formatAvailableEvents(StateConfig state) {
        if (state.getTransitions() == null) return "";
        StringBuilder sb = new StringBuilder();
        Map<String, String> labels = Map.of(
                "submit", "提交订单", "review", "审核", "pay", "支付",
                "ship", "发货", "retry", "重试");
        for (StateTransition t : state.getTransitions()) {
            if (t.getEvent() != null) {
                String label = labels.getOrDefault(t.getEvent(), t.getEvent());
                sb.append(label).append("(").append(t.getEvent()).append(") ");
            }
        }
        return sb.toString().trim();
    }

    private StateConfig findState(String name) {
        for (StateConfig s : states) {
            if (s.getName().equals(name)) return s;
        }
        return null;
    }

    public String getCurrentState() {
        return currentState;
    }

    public void reset() {
        currentState = states.isEmpty() ? null : states.get(0).getName();
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    private Msg buildTextMsg(String text) {
        return Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text(text).build())
                .build();
    }

    private void emit(String type, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>(data);
        payload.put("type", type);
        for (var consumer : eventConsumers) {
            consumer.accept(type, payload);
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/skloda/agentscope/composite/graph/OrderFulfillmentGraph.java
git commit -m "feat: add OrderFulfillmentGraph custom state machine"
```

---

## Task 5: MsgHub — RoundTablePipeline and MsgHubRuntime

**Files:**
- Create: `src/main/java/com/skloda/agentscope/composite/pipeline/RoundTablePipeline.java`
- Create: `src/main/java/com/skloda/agentscope/runtime/MsgHubRuntime.java`

- [ ] **Step 1: Create RoundTablePipeline.java**

```java
package com.skloda.agentscope.composite.pipeline;

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.pipeline.Pipeline;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Expert roundtable pipeline using sequential multi-round discussion.
 * Each round, all experts speak in order, seeing previous messages.
 * After all rounds, moderator synthesizes a summary.
 */
public class RoundTablePipeline implements Pipeline<Msg> {

    private final AgentBase moderator;
    private final List<AgentBase> experts;
    private final int rounds;
    private final List<BiConsumer<String, Map<String, Object>>> eventConsumers = new ArrayList<>();

    public RoundTablePipeline(AgentBase moderator, List<AgentBase> experts, int rounds) {
        this.moderator = moderator;
        this.experts = experts;
        this.rounds = rounds;
    }

    public void addEventConsumer(BiConsumer<String, Map<String, Object>> consumer) {
        eventConsumers.add(consumer);
    }

    @Override
    public Mono<Msg> execute(Msg input) {
        return execute(input, null);
    }

    @Override
    public Mono<Msg> execute(Msg input, Class<?> structuredOutputClass) {
        List<String> participantNames = new ArrayList<>();
        for (AgentBase e : experts) {
            participantNames.add(e.getName() != null ? e.getName() : e.getClass().getSimpleName());
        }

        emit("roundtable_start", Map.of(
                "participants", participantNames,
                "rounds", rounds
        ));

        return runRounds(input, 1, new ArrayList<>())
                .flatMap(allDiscussion -> {
                    // Moderator summarizes
                    Msg summaryMsg = buildSummaryMessage(input, allDiscussion);
                    return moderator.call(summaryMsg)
                            .map(summary -> {
                                String summaryText = extractText(summary);
                                emit("roundtable_summary", Map.of(
                                        "agent", moderator.getName() != null ? moderator.getName() : "moderator",
                                        "content", truncate(summaryText, 500)
                                ));
                                return summary;
                            });
                });
    }

    private Mono<List<String>> runRounds(Msg originalInput, int currentRound, List<String> allDiscussion) {
        if (currentRound > rounds) {
            return Mono.just(allDiscussion);
        }

        emit("round_start", Map.of("round", currentRound));

        // Each expert speaks in order, accumulating discussion
        return runExpertRound(originalInput, currentRound, allDiscussion, 0)
                .flatMap(updatedDiscussion -> {
                    emit("round_end", Map.of("round", currentRound));
                    return runRounds(originalInput, currentRound + 1, updatedDiscussion);
                });
    }

    private Mono<List<String>> runExpertRound(Msg originalInput, int round, List<String> discussion, int expertIndex) {
        if (expertIndex >= experts.size()) {
            return Mono.just(discussion);
        }

        AgentBase expert = experts.get(expertIndex);
        String expertName = expert.getName() != null ? expert.getName() : ("expert-" + expertIndex);

        // Build message with all previous discussion context
        Msg expertMsg = buildExpertMessage(originalInput, round, discussion);
        return expert.call(expertMsg)
                .map(response -> {
                    String responseText = extractText(response);
                    emit("round_message", Map.of(
                            "agent", expertName,
                            "content", truncate(responseText, 300)
                    ));
                    List<String> updated = new ArrayList<>(discussion);
                    updated.add("**" + expertName + " (第" + round + "轮):**\n" + responseText);
                    return updated;
                })
                .flatMap(updated -> runExpertRound(originalInput, round, updated, expertIndex + 1));
    }

    private Msg buildExpertMessage(Msg originalInput, int round, List<String> discussion) {
        StringBuilder sb = new StringBuilder();
        // Original topic
        sb.append("## 评审主题\n\n");
        for (ContentBlock block : originalInput.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            }
        }

        // Previous discussion context
        if (!discussion.isEmpty()) {
            sb.append("\n\n## 历史讨论\n\n");
            for (String msg : discussion) {
                sb.append(msg).append("\n\n");
            }
        }

        sb.append("\n\n---\n\n这是第 ").append(round).append(" 轮讨论。");
        if (round == 1) {
            sb.append("请提出你的专业观点。");
        } else if (round < rounds) {
            sb.append("请回应其他专家的观点，进一步深入讨论。");
        } else {
            sb.append("最后一轮，请尝试达成共识或总结你的核心观点。");
        }

        return Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(sb.toString()).build())
                .build();
    }

    private Msg buildSummaryMessage(Msg originalInput, List<String> allDiscussion) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 评审主题\n\n");
        for (ContentBlock block : originalInput.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            }
        }
        sb.append("\n\n## 所有讨论记录\n\n");
        for (String msg : allDiscussion) {
            sb.append(msg).append("\n\n");
        }
        sb.append("---\n\n请综合所有专家的观点，给出最终的评审报告，包括：\n");
        sb.append("1. 各专家核心观点摘要\n");
        sb.append("2. 共识与分歧\n");
        sb.append("3. 最终建议");

        return Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(sb.toString()).build())
                .build();
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private void emit(String type, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>(data);
        payload.put("type", type);
        for (var consumer : eventConsumers) {
            consumer.accept(type, payload);
        }
    }
}
```

- [ ] **Step 2: Create MsgHubRuntime.java**

```java
package com.skloda.agentscope.runtime;

import com.skloda.agentscope.composite.pipeline.RoundTablePipeline;
import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Runtime for MsgHub-based roundtable discussions.
 * Manages the RoundTablePipeline lifecycle and event emission.
 */
public class MsgHubRuntime implements StreamingAgentRuntime {

    private final String pipelineName;
    private final RoundTablePipeline pipeline;
    private final ObservabilityHook hook;
    private final Sinks.Many<Map<String, Object>> sink;
    private final BiConsumer<String, Map<String, Object>> hookBridge;

    public MsgHubRuntime(String pipelineName, RoundTablePipeline pipeline, ObservabilityHook hook) {
        this.pipelineName = pipelineName;
        this.pipeline = pipeline;
        this.hook = hook;
        this.sink = Sinks.many().multicast().onBackpressureBuffer();

        this.hookBridge = (type, data) -> {
            Map<String, Object> payload = new LinkedHashMap<>(data);
            payload.put("type", type);
            emit(payload);
        };
        hook.addConsumer(hookBridge);

        // Bridge pipeline events to the sink
        pipeline.addEventConsumer((type, data) -> {
            Map<String, Object> payload = new LinkedHashMap<>(data);
            payload.put("type", type);
            emit(payload);
        });
    }

    @Override
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        Flux<Map<String, Object>> pipelineStream = Flux.<Map<String, Object>>create(fluxSink -> {
            try {
                hook.emitPipelineStart(pipelineName, List.of());
                long startNanos = System.nanoTime();

                Msg result = pipeline.execute(userMsg).block();
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

                // Emit final text from result
                if (result != null && result.getContent() != null) {
                    for (ContentBlock block : result.getContent()) {
                        if (block instanceof TextBlock tb) {
                            String text = tb.getText();
                            if (text != null && !text.isEmpty()) {
                                fluxSink.next(Map.of("type", "text", "content", text));
                            }
                        }
                    }
                }

                hook.emitPipelineEnd(pipelineName, 1, durationMs);
                fluxSink.next(Map.of("type", "done"));
                fluxSink.complete();
            } catch (Exception e) {
                fluxSink.next(Map.of("type", "error", "message", e.getMessage()));
                fluxSink.complete();
            }
        });

        return Flux.merge(this.sink.asFlux(), pipelineStream)
                .doOnCancel(this::close)
                .doOnComplete(this::close);
    }

    @Override
    public ObservabilityHook getHook() {
        return hook;
    }

    private void emit(Map<String, Object> payload) {
        Sinks.EmitResult result = sink.tryEmitNext(payload);
        if (result.isFailure()) {
            // sink may be closed, ignore
        }
    }

    @Override
    public void close() {
        hook.removeConsumer(hookBridge);
        hook.reset();
        sink.tryEmitComplete();
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/skloda/agentscope/composite/pipeline/RoundTablePipeline.java \
        src/main/java/com/skloda/agentscope/runtime/MsgHubRuntime.java
git commit -m "feat: add RoundTablePipeline and MsgHubRuntime for expert roundtable"
```

---

## Task 6: Subagents — TaskOrchestratorPipeline and TaskDispatcherPipeline

**Files:**
- Create: `src/main/java/com/skloda/agentscope/composite/pipeline/TaskOrchestratorPipeline.java`
- Create: `src/main/java/com/skloda/agentscope/composite/pipeline/TaskDispatcherPipeline.java`

- [ ] **Step 1: Create TaskOrchestratorPipeline.java**

```java
package com.skloda.agentscope.composite.pipeline;

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.pipeline.Pipeline;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Sequential task orchestration pipeline.
 * Each step receives the previous step's output via {prevOutput} template variable.
 */
public class TaskOrchestratorPipeline implements Pipeline<Msg> {

    private final List<AgentBase> agents;
    private final List<String> taskTemplates;
    private final List<String> agentIds;
    private final List<BiConsumer<String, Map<String, Object>>> eventConsumers = new ArrayList<>();

    public TaskOrchestratorPipeline(List<AgentBase> agents, List<String> taskTemplates, List<String> agentIds) {
        this.agents = agents;
        this.taskTemplates = taskTemplates;
        this.agentIds = agentIds;
    }

    public void addEventConsumer(BiConsumer<String, Map<String, Object>> consumer) {
        eventConsumers.add(consumer);
    }

    @Override
    public Mono<Msg> execute(Msg input) {
        return execute(input, null);
    }

    @Override
    public Mono<Msg> execute(Msg input, Class<?> structuredOutputClass) {
        String inputText = extractText(input);
        return runStep(0, inputText, null);
    }

    private Mono<Msg> runStep(int index, String originalInput, String prevOutput) {
        if (index >= agents.size()) {
            return Mono.just(buildTextMsg(prevOutput != null ? prevOutput : "No output"));
        }

        AgentBase agent = agents.get(index);
        String agentId = index < agentIds.size() ? agentIds.get(index) : ("agent-" + index);
        String template = index < taskTemplates.size() ? taskTemplates.get(index) : "{input}";

        // Render template
        String task = template;
        if (prevOutput != null) {
            task = task.replace("{prevOutput}", prevOutput);
        }
        task = task.replace("{input}", originalInput);

        emit("task_delegate", Map.of(
                "from", "orchestrator",
                "to", agentId,
                "task", truncate(task, 200)
        ));
        emit("task_start", Map.of("agent", agentId));

        Msg agentMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(task).build())
                .build();

        return agent.call(agentMsg)
                .flatMap(output -> {
                    String outputText = extractText(output);
                    emit("task_end", Map.of(
                            "agent", agentId,
                            "outputPreview", truncate(outputText, 200)
                    ));
                    return runStep(index + 1, originalInput, outputText);
                });
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    private Msg buildTextMsg(String text) {
        return Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text(text).build())
                .build();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private void emit(String type, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>(data);
        payload.put("type", type);
        for (var consumer : eventConsumers) {
            consumer.accept(type, payload);
        }
    }
}
```

- [ ] **Step 2: Create TaskDispatcherPipeline.java**

```java
package com.skloda.agentscope.composite.pipeline;

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.pipeline.Pipeline;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Parallel task delegation pipeline.
 * All agents receive the same input (rendered via taskTemplate) concurrently.
 * Results are aggregated.
 */
public class TaskDispatcherPipeline implements Pipeline<Msg> {

    private final List<AgentBase> agents;
    private final List<String> taskTemplates;
    private final List<String> agentIds;
    private final List<BiConsumer<String, Map<String, Object>>> eventConsumers = new ArrayList<>();

    public TaskDispatcherPipeline(List<AgentBase> agents, List<String> taskTemplates, List<String> agentIds) {
        this.agents = agents;
        this.taskTemplates = taskTemplates;
        this.agentIds = agentIds;
    }

    public void addEventConsumer(BiConsumer<String, Map<String, Object>> consumer) {
        eventConsumers.add(consumer);
    }

    @Override
    public Mono<Msg> execute(Msg input) {
        return execute(input, null);
    }

    @Override
    public Mono<Msg> execute(Msg input, Class<?> structuredOutputClass) {
        String inputText = extractText(input);

        // Dispatch to all agents in parallel
        List<Mono<Msg>> agentMonos = new ArrayList<>();
        for (int i = 0; i < agents.size(); i++) {
            AgentBase agent = agents.get(i);
            String agentId = i < agentIds.size() ? agentIds.get(i) : ("agent-" + i);
            String template = i < taskTemplates.size() ? taskTemplates.get(i) : "{input}";
            String task = template.replace("{input}", inputText);

            emit("task_delegate", Map.of(
                    "from", "dispatcher",
                    "to", agentId,
                    "task", truncate(task, 200)
            ));
            emit("task_start", Map.of("agent", agentId));

            Msg agentMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(TextBlock.builder().text(task).build())
                    .build();

            agentMonos.add(agent.call(agentMsg).map(output -> {
                String outputText = extractText(output);
                emit("task_end", Map.of(
                        "agent", agentId,
                        "outputPreview", truncate(outputText, 200)
                ));
                return output;
            }));
        }

        return Flux.merge(agentMonos)
                .collectList()
                .map(results -> {
                    emit("task_aggregate", Map.of("totalTasks", results.size()));

                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < results.size(); i++) {
                        String agentId = i < agentIds.size() ? agentIds.get(i) : ("agent-" + i);
                        sb.append("## ").append(agentId).append(" 的结果\n\n");
                        String text = extractText(results.get(i));
                        sb.append(text).append("\n\n");
                    }
                    return Msg.builder()
                            .role(MsgRole.ASSISTANT)
                            .content(TextBlock.builder().text(sb.toString()).build())
                            .build();
                });
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private void emit(String type, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>(data);
        payload.put("type", type);
        for (var consumer : eventConsumers) {
            consumer.accept(type, payload);
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/skloda/agentscope/composite/pipeline/TaskOrchestratorPipeline.java \
        src/main/java/com/skloda/agentscope/composite/pipeline/TaskDispatcherPipeline.java
git commit -m "feat: add TaskOrchestratorPipeline and TaskDispatcherPipeline for subagents"
```

---

## Task 7: StateGraphRuntime — Standalone Runtime

**Files:**
- Create: `src/main/java/com/skloda/agentscope/runtime/StateGraphRuntime.java`

Since `OrderFulfillmentGraph` is not a `Pipeline`, we need a standalone `StreamingAgentRuntime` implementation for it (same pattern as `MsgHubRuntime`).

- [ ] **Step 1: Create StateGraphRuntime.java**

```java
package com.skloda.agentscope.runtime;

import com.skloda.agentscope.composite.graph.OrderFulfillmentGraph;
import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Runtime for StateGraph-based agents.
 * Wraps OrderFulfillmentGraph and bridges events to the SSE stream.
 */
public class StateGraphRuntime implements StreamingAgentRuntime {

    private final String graphName;
    private final OrderFulfillmentGraph graph;
    private final ObservabilityHook hook;
    private final Sinks.Many<Map<String, Object>> sink;
    private final BiConsumer<String, Map<String, Object>> hookBridge;

    public StateGraphRuntime(String graphName, OrderFulfillmentGraph graph, ObservabilityHook hook) {
        this.graphName = graphName;
        this.graph = graph;
        this.hook = hook;
        this.sink = Sinks.many().multicast().onBackpressureBuffer();

        this.hookBridge = (type, data) -> {
            Map<String, Object> payload = new LinkedHashMap<>(data);
            payload.put("type", type);
            emit(payload);
        };
        hook.addConsumer(hookBridge);

        // Bridge graph events to the sink
        graph.addEventConsumer((type, data) -> {
            Map<String, Object> payload = new LinkedHashMap<>(data);
            payload.put("type", type);
            emit(payload);
        });
    }

    @Override
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        Flux<Map<String, Object>> graphStream = Flux.<Map<String, Object>>create(fluxSink -> {
            try {
                hook.emitPipelineStart(graphName, List.of());
                long startNanos = System.nanoTime();

                Msg result = graph.execute(userMsg).block();
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

                if (result != null && result.getContent() != null) {
                    for (ContentBlock block : result.getContent()) {
                        if (block instanceof TextBlock tb) {
                            String text = tb.getText();
                            if (text != null && !text.isEmpty()) {
                                fluxSink.next(Map.of("type", "text", "content", text));
                            }
                        }
                    }
                }

                hook.emitPipelineEnd(graphName, 1, durationMs);
                fluxSink.next(Map.of("type", "done"));
                fluxSink.complete();
            } catch (Exception e) {
                fluxSink.next(Map.of("type", "error", "message", e.getMessage()));
                fluxSink.complete();
            }
        });

        return Flux.merge(this.sink.asFlux(), graphStream)
                .doOnCancel(this::close)
                .doOnComplete(this::close);
    }

    @Override
    public ObservabilityHook getHook() {
        return hook;
    }

    private void emit(Map<String, Object> payload) {
        sink.tryEmitNext(payload);
    }

    @Override
    public void close() {
        hook.removeConsumer(hookBridge);
        hook.reset();
        sink.tryEmitComplete();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/skloda/agentscope/runtime/StateGraphRuntime.java
git commit -m "feat: add StateGraphRuntime for StateGraph-based agents"
```

---

## Task 8: CompositeAgentFactory — New Factory Methods

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java`

- [ ] **Step 1: Add import statements**

Add these imports at the top of `CompositeAgentFactory.java` after existing imports:

```java
import com.skloda.agentscope.agent.LoopConfig;
import com.skloda.agentscope.agent.MsgHubConfig;
import com.skloda.agentscope.agent.StateConfig;
import com.skloda.agentscope.composite.graph.OrderFulfillmentGraph;
import com.skloda.agentscope.composite.pipeline.LoopPipeline;
import com.skloda.agentscope.composite.pipeline.RoundTablePipeline;
import com.skloda.agentscope.composite.pipeline.TaskDispatcherPipeline;
import com.skloda.agentscope.composite.pipeline.TaskOrchestratorPipeline;
```

- [ ] **Step 2: Add createLoopAgent method**

After the `createDebateAgent` method (~line 191), add:

```java
    public LoopPipeline createLoopAgent(AgentConfig config, Memory memory) {
        List<SubAgentConfig> subs = config.getSubAgents();
        if (subs.size() < 2) {
            throw new IllegalArgumentException("LOOP requires at least 2 sub-agents (writer + critic)");
        }

        AgentBase writer = findOrCreateAgent(subs.get(0), memory);
        AgentBase critic = findOrCreateAgent(subs.get(1), memory);

        LoopConfig loopConfig = config.getLoopConfig();
        int maxIterations = loopConfig != null ? loopConfig.getMaxIterations() : 3;
        boolean autoExit = loopConfig == null || "AUTO".equalsIgnoreCase(loopConfig.getExitCondition());

        return new LoopPipeline(writer, critic, maxIterations, autoExit);
    }
```

- [ ] **Step 3: Add createStateGraphAgent method**

```java
    public OrderFulfillmentGraph createStateGraphAgent(AgentConfig config, Memory memory) {
        List<StateConfig> states = config.getStates();
        if (states == null || states.isEmpty()) {
            throw new IllegalArgumentException("STATE_GRAPH requires states configuration");
        }

        Map<String, ReActAgent> stateAgents = new LinkedHashMap<>();
        for (StateConfig state : states) {
            if (state.getAgent() != null) {
                AgentConfig agentConfig = configService.getAgentConfig(state.getAgent());
                ReActAgent agent = (ReActAgent) agentFactory.createAgent(agentConfig, memory);
                stateAgents.put(state.getName(), agent);
            }
        }

        return new OrderFulfillmentGraph(states, stateAgents);
    }
```

- [ ] **Step 4: Add createMsgHubAgent method**

```java
    public RoundTablePipeline createMsgHubAgent(AgentConfig config, Memory memory) {
        List<SubAgentConfig> subs = config.getSubAgents();
        if (subs.size() < 2) {
            throw new IllegalArgumentException("MSG_HUB requires at least a moderator + experts");
        }

        // First sub-agent is moderator
        AgentBase moderator = findOrCreateAgent(subs.get(0), memory);

        // Rest are experts
        List<AgentBase> experts = new ArrayList<>();
        for (int i = 1; i < subs.size(); i++) {
            experts.add(findOrCreateAgent(subs.get(i), memory));
        }

        MsgHubConfig msgHubConfig = config.getMsgHubConfig();
        int rounds = msgHubConfig != null ? msgHubConfig.getRounds() : 3;

        return new RoundTablePipeline(moderator, experts, rounds);
    }
```

- [ ] **Step 5: Add createSubagentSeqAgent method**

```java
    public TaskOrchestratorPipeline createSubagentSeqAgent(AgentConfig config, Memory memory) {
        List<SubAgentConfig> subs = config.getSubAgents();
        if (subs.isEmpty()) {
            throw new IllegalArgumentException("SUBAGENT_SEQ requires at least 1 sub-agent");
        }

        List<AgentBase> agents = new ArrayList<>();
        List<String> templates = new ArrayList<>();
        List<String> ids = new ArrayList<>();

        for (SubAgentConfig sub : subs) {
            agents.add(findOrCreateAgent(sub, memory));
            templates.add(sub.getTaskTemplate() != null ? sub.getTaskTemplate() : "{input}");
            ids.add(sub.getAgentId());
        }

        return new TaskOrchestratorPipeline(agents, templates, ids);
    }
```

- [ ] **Step 6: Add createSubagentParAgent method**

```java
    public TaskDispatcherPipeline createSubagentParAgent(AgentConfig config, Memory memory) {
        List<SubAgentConfig> subs = config.getSubAgents();
        if (subs.isEmpty()) {
            throw new IllegalArgumentException("SUBAGENT_PAR requires at least 1 sub-agent");
        }

        List<AgentBase> agents = new ArrayList<>();
        List<String> templates = new ArrayList<>();
        List<String> ids = new ArrayList<>();

        for (SubAgentConfig sub : subs) {
            agents.add(findOrCreateAgent(sub, memory));
            templates.add(sub.getTaskTemplate() != null ? sub.getTaskTemplate() : "{input}");
            ids.add(sub.getAgentId());
        }

        return new TaskDispatcherPipeline(agents, templates, ids);
    }
```

- [ ] **Step 7: Add helper method findOrCreateAgent**

```java
    private AgentBase findOrCreateAgent(SubAgentConfig subConfig, Memory memory) {
        AgentConfig agentConfig = configService.getAgentConfig(subConfig.getAgentId());
        return agentFactory.createAgent(agentConfig, memory);
    }
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java
git commit -m "feat: add P6 factory methods to CompositeAgentFactory"
```

---

## Task 9: AgentRuntimeFactory — New Runtime Creation Methods

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/runtime/AgentRuntimeFactory.java`

- [ ] **Step 1: Add imports**

After existing imports, add:

```java
import com.skloda.agentscope.composite.graph.OrderFulfillmentGraph;
import com.skloda.agentscope.composite.pipeline.LoopPipeline;
import com.skloda.agentscope.composite.pipeline.RoundTablePipeline;
import com.skloda.agentscope.composite.pipeline.TaskOrchestratorPipeline;
import com.skloda.agentscope.composite.pipeline.TaskDispatcherPipeline;
```

- [ ] **Step 2: Add cases to createRuntime switch**

In `createRuntime()` method (line 41-48), add new cases after `case DEBATE`:

```java
            case LOOP -> createLoopRuntime(agentId);
            case STATE_GRAPH -> createStateGraphRuntime(agentId);
            case MSG_HUB -> createMsgHubRuntime(agentId);
            case SUBAGENT_SEQ -> createSubagentSeqRuntime(agentId);
            case SUBAGENT_PAR -> createSubagentParRuntime(agentId);
```

- [ ] **Step 3: Add cases to createRuntimeWithMemory switch**

In `createRuntimeWithMemory()` method (line 57-64), add after `case DEBATE`:

```java
            case LOOP -> createLoopRuntimeWithMemory(agentId, memory);
            case STATE_GRAPH -> createStateGraphRuntimeWithMemory(agentId, memory);
            case MSG_HUB -> createMsgHubRuntimeWithMemory(agentId, memory);
            case SUBAGENT_SEQ -> createSubagentSeqRuntimeWithMemory(agentId, memory);
            case SUBAGENT_PAR -> createSubagentParRuntimeWithMemory(agentId, memory);
```

- [ ] **Step 4: Add runtime creation methods**

After the existing `createDebateRuntimeWithMemory` method (~line 137), add:

```java
    public PipelineAgentRuntime createLoopRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        LoopPipeline pipeline = compositeFactory.createLoopAgent(config, null);
        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    public PipelineAgentRuntime createLoopRuntimeWithMemory(String agentId, Memory memory) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        LoopPipeline pipeline = compositeFactory.createLoopAgent(config, memory);
        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    public AgentRuntime createStateGraphRuntime(String agentId) {
        ObservabilityHook hook = new ObservabilityHook();
        OrderFulfillmentGraph graph = compositeFactory.createStateGraphAgent(
                configService.getAgentConfig(agentId), null);
        return new StateGraphRuntime(agentId, graph, hook);
    }

    public AgentRuntime createStateGraphRuntimeWithMemory(String agentId, Memory memory) {
        ObservabilityHook hook = new ObservabilityHook();
        OrderFulfillmentGraph graph = compositeFactory.createStateGraphAgent(
                configService.getAgentConfig(agentId), memory);
        return new StateGraphRuntime(agentId, graph, hook);
    }

    public MsgHubRuntime createMsgHubRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        RoundTablePipeline pipeline = compositeFactory.createMsgHubAgent(config, null);
        return new MsgHubRuntime(config.getAgentId(), pipeline, hook);
    }

    public MsgHubRuntime createMsgHubRuntimeWithMemory(String agentId, Memory memory) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        RoundTablePipeline pipeline = compositeFactory.createMsgHubAgent(config, memory);
        return new MsgHubRuntime(config.getAgentId(), pipeline, hook);
    }

    public PipelineAgentRuntime createSubagentSeqRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        TaskOrchestratorPipeline pipeline = compositeFactory.createSubagentSeqAgent(config, null);
        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    public PipelineAgentRuntime createSubagentSeqRuntimeWithMemory(String agentId, Memory memory) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        TaskOrchestratorPipeline pipeline = compositeFactory.createSubagentSeqAgent(config, memory);
        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    public PipelineAgentRuntime createSubagentParRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        TaskDispatcherPipeline pipeline = compositeFactory.createSubagentParAgent(config, null);
        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    public PipelineAgentRuntime createSubagentParRuntimeWithMemory(String agentId, Memory memory) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        TaskDispatcherPipeline pipeline = compositeFactory.createSubagentParAgent(config, memory);
        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }
```

- [ ] **Step 5: Verify StateGraphRuntime import**

`StateGraphRuntime` was created in Task 7. Ensure it's imported in this file:

```java
import com.skloda.agentscope.runtime.StateGraphRuntime;
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/skloda/agentscope/runtime/AgentRuntimeFactory.java \
        src/main/java/com/skloda/agentscope/runtime/StateGraphRuntime.java
git commit -m "feat: add P6 runtime creation methods to AgentRuntimeFactory"
```

---

## Task 10: Agent YAML Configuration

**Files:**
- Modify: `src/main/resources/config/agents.yml`

- [ ] **Step 1: Add Loop Pipeline agents**

At the end of the single agents section (before expert agents), add writer and critic:

```yaml
  # Loop Pipeline sub-agents
  - agentId: writer
    name: 文案写手
    description: 负责撰写和修改文案内容
    systemPrompt: |
      你是一位专业的文案写手。你需要根据用户的需求撰写高质量的内容。
      如果收到评审意见，请根据意见修改你的内容，保留核心内容同时改进不足之处。
      直接输出文案内容，不要添加额外说明。
    modelName: qwen-plus
    enableThinking: false
    streaming: true
    category: expert

  - agentId: critic
    name: 文案评审
    description: 负责评审文案质量
    systemPrompt: |
      你是一位严格的文案评审专家。评审以下内容时：
      - 如果内容质量足够好，回复中包含【通过】
      - 如果需要修改，给出具体的修改建议
      评审要点：逻辑清晰、内容完整、语言流畅、符合需求。
    modelName: qwen-plus
    enableThinking: false
    streaming: true
    category: expert
```

In the multi-agent collaboration section, add:

```yaml
  # === P6: Loop Pipeline ===
  - agentId: copywriter-refiner
    name: 文案打磨
    description: 写手撰写→评审把关→反复修改，直到文案满意
    type: LOOP
    category: collaboration
    loopConfig:
      maxIterations: 3
      exitCondition: AUTO
    subAgents:
      - agentId: writer
        role: WRITER
      - agentId: critic
        role: CRITIC
    samplePrompts:
      - prompt: 写一篇关于AI助手的产品介绍
        expectedBehavior: 撰写→评审→修改，最多3轮
```

- [ ] **Step 2: Add StateGraph agents**

Add sub-agents in single section:

```yaml
  # StateGraph sub-agents
  - agentId: order-reviewer
    name: 订单审核员
    description: 审核订单信息
    systemPrompt: |
      你是订单审核员。根据用户提供的订单信息判断是否通过审核。
      审核标准：信息完整、金额合理、无异常。
      用【决策:approved】表示通过，【决策:rejected】表示拒绝。
      简要说明审核结果和原因。
    modelName: qwen-plus
    enableThinking: false
    streaming: true
    category: expert

  - agentId: payment-agent
    name: 支付处理
    description: 处理订单支付
    systemPrompt: |
      你是支付处理系统。模拟处理支付请求。
      大部分情况支付成功，偶尔可能失败。
      用【决策:success】表示支付成功，【决策:fail】表示支付失败。
      简要说明支付处理结果。
    modelName: qwen-plus
    enableThinking: false
    streaming: true
    category: expert

  - agentId: shipping-agent
    name: 物流处理
    description: 处理订单发货
    systemPrompt: |
      你是物流处理系统。模拟处理发货请求。
      大部分情况发货成功，偶尔可能丢失。
      用【决策:delivered】表示送达，【决策:lost】表示丢失。
      简要说明物流处理结果。
    modelName: qwen-plus
    enableThinking: false
    streaming: true
    category: expert
```

Add multi-agent config:

```yaml
  # === P6: StateGraph ===
  - agentId: order-fulfillment
    name: 订单履约
    description: 模拟完整的订单履约流程（提交→审核→支付→发货）
    type: STATE_GRAPH
    category: collaboration
    states:
      - name: CREATED
        transitions:
          - event: submit
            target: SUBMITTED
      - name: SUBMITTED
        transitions:
          - event: review
            target: REVIEWING
      - name: REVIEWING
        agent: order-reviewer
        transitions:
          - condition: approved
            target: APPROVED
          - condition: rejected
            target: REJECTED
      - name: APPROVED
        transitions:
          - event: pay
            target: PAYING
      - name: PAYING
        agent: payment-agent
        transitions:
          - condition: success
            target: PAID
          - condition: fail
            target: PAY_FAILED
      - name: PAY_FAILED
        transitions:
          - event: retry
            target: PAYING
      - name: PAID
        transitions:
          - event: ship
            target: SHIPPING
      - name: SHIPPING
        agent: shipping-agent
        transitions:
          - condition: delivered
            target: DONE
          - condition: lost
            target: LOST_IN_TRANSIT
      - name: REJECTED
        transitions: []
      - name: DONE
        transitions: []
      - name: LOST_IN_TRANSIT
        transitions: []
    samplePrompts:
      - prompt: 提交订单
        expectedBehavior: 启动订单履约流程
```

- [ ] **Step 3: Add MsgHub agents**

Add sub-agents in single section:

```yaml
  # MsgHub sub-agents
  - agentId: moderator
    name: 评审主持人
    description: 主持专家圆桌讨论并总结
    systemPrompt: |
      你是技术方案评审的主持人。你需要综合所有专家的意见，给出最终的评审报告。
      报告应包括：各专家核心观点摘要、共识与分歧、最终建议。
    modelName: qwen-plus
    enableThinking: false
    streaming: true
    category: expert

  - agentId: architect
    name: 架构师
    description: 架构设计专家
    systemPrompt: |
      你是资深架构师。从系统架构、技术选型、可扩展性等角度评审技术方案。
      给出具体的架构建议和潜在风险。
    modelName: qwen-plus
    enableThinking: false
    streaming: true
    category: expert

  - agentId: dba-expert
    name: DBA专家
    description: 数据库和存储专家
    systemPrompt: |
      你是数据库管理专家。从数据模型、存储方案、查询性能、数据安全等角度评审技术方案。
      给出具体的数据库优化建议。
    modelName: qwen-plus
    enableThinking: false
    streaming: true
    category: expert

  - agentId: security-expert
    name: 安全专家
    description: 安全和合规专家
    systemPrompt: |
      你是信息安全专家。从安全架构、权限控制、数据保护、合规性等角度评审技术方案。
      给出具体的安全改进建议。
    modelName: qwen-plus
    enableThinking: false
    streaming: true
    category: expert
```

Add multi-agent config:

```yaml
  # === P6: MsgHub Roundtable ===
  - agentId: expert-roundtable
    name: 专家圆桌评审
    description: 多位专家对技术方案进行多轮讨论评审
    type: MSG_HUB
    category: collaboration
    msgHubConfig:
      rounds: 2
      summaryRole: MODERATOR
    subAgents:
      - agentId: moderator
        role: MODERATOR
      - agentId: architect
        role: EXPERT
      - agentId: dba-expert
        role: EXPERT
      - agentId: security-expert
        role: EXPERT
    samplePrompts:
      - prompt: 评审方案：使用微服务架构重构现有单体应用，采用Spring Cloud + Kubernetes
        expectedBehavior: 架构师、DBA、安全专家分别讨论，主持人总结
```

- [ ] **Step 4: Add Subagent agents**

Add sub-agents in single section:

```yaml
  # Subagent sub-agents
  - agentId: researcher
    name: 调研员
    description: 负责主题调研
    systemPrompt: 你是专业调研员。根据给定主题进行深入调研，提供全面的背景信息和最新进展。
    modelName: qwen-plus
    enableThinking: false
    streaming: true
    category: expert

  - agentId: analyst
    name: 分析师
    description: 负责数据分析
    systemPrompt: 你是数据分析专家。对给定的调研结果进行深度分析，提取关键洞察和趋势。
    modelName: qwen-plus
    enableThinking: false
    streaming: true
    category: expert

  - agentId: report-writer
    name: 报告撰写员
    description: 负责撰写最终报告
    systemPrompt: 你是专业报告撰写员。根据给定的分析结果，撰写结构清晰、内容完整的最终报告。
    modelName: qwen-plus
    enableThinking: false
    streaming: true
    category: expert

  - agentId: pm-researcher
    name: 项目调研员
    description: 调研项目可行性
    systemPrompt: 你是项目调研员。调研项目的技术可行性和市场情况，提供全面的可行性分析。
    modelName: qwen-plus
    enableThinking: false
    streaming: true
    category: expert

  - agentId: pm-designer
    name: 方案设计师
    description: 设计技术方案
    systemPrompt: 你是技术方案设计师。根据需求设计技术方案和系统架构，提供详细的设计文档。
    modelName: qwen-plus
    enableThinking: false
    streaming: true
    category: expert

  - agentId: pm-evaluator
    name: 风险评估员
    description: 评估项目风险
    systemPrompt: 你是项目风险评估员。评估项目的风险和资源需求，提供全面的风险分析报告。
    modelName: qwen-plus
    enableThinking: false
    streaming: true
    category: expert
```

Add multi-agent configs:

```yaml
  # === P6: Subagents Sequential ===
  - agentId: report-generator
    name: 报告生成流水线
    description: 调研→分析→撰写，自动生成结构化报告
    type: SUBAGENT_SEQ
    category: collaboration
    subAgents:
      - agentId: researcher
        role: RESEARCHER
        taskTemplate: "请调研以下主题的背景信息和最新进展：{input}"
      - agentId: analyst
        role: ANALYST
        taskTemplate: "基于以下调研结果进行深度分析：{prevOutput}"
      - agentId: report-writer
        role: WRITER
        taskTemplate: "根据以下分析结果撰写结构化报告：{prevOutput}"
    samplePrompts:
      - prompt: 生成一份关于大语言模型在企业级应用中的现状与发展趋势的报告
        expectedBehavior: 调研→分析→撰写三步生成报告

  # === P6: Subagents Parallel ===
  - agentId: project-manager
    name: 项目经理委派
    description: 将项目需求同时委派给调研、设计、评估三个专家
    type: SUBAGENT_PAR
    category: collaboration
    subAgents:
      - agentId: pm-researcher
        role: RESEARCHER
        taskTemplate: "调研项目的技术可行性和市场情况：{input}"
      - agentId: pm-designer
        role: DESIGNER
        taskTemplate: "设计项目的技术方案和架构：{input}"
      - agentId: pm-evaluator
        role: EVALUATOR
        taskTemplate: "评估项目的风险和资源需求：{input}"
    samplePrompts:
      - prompt: 开发一个基于AI的智能客服系统
        expectedBehavior: 调研、设计、评估三路并行
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/config/agents.yml
git commit -m "feat: add P6 agent configurations for all 5 advanced patterns"
```

---

## Task 11: Frontend — Debug Panel Event Handlers

**Files:**
- Modify: `src/main/resources/static/scripts/modules/debug.js`
- Modify: `src/main/resources/static/scripts/chat.js`

- [ ] **Step 1: Add new event handler functions to debug.js**

After the existing `handleHandoffStart` function (~line 406), add:

```javascript
/* ===== P6 ADVANCED PATTERN EVENT HANDLERS ===== */

export function handleLoopStart(data) {
    var targetRound = window.currentRound || (window.rounds.length > 0 ? window.rounds[window.rounds.length - 1] : null);
    if (!targetRound) return;
    addTimelineRowForRound(targetRound, 'phase', 'Loop #' + (data.iteration || '?'), '', 'running');
}

export function handleLoopEnd(data) {
    var targetRound = window.currentRound || (window.rounds.length > 0 ? window.rounds[window.rounds.length - 1] : null);
    if (!targetRound) return;
    var status = data.finalApproved ? 'ok' : 'fail';
    addTimelineRowForRound(targetRound, 'phase', 'Loop End',
        (data.totalIterations || 0) + ' iterations', status);
}

export function handleLoopIterationResult(data) {
    var targetRound = window.currentRound || (window.rounds.length > 0 ? window.rounds[window.rounds.length - 1] : null);
    if (!targetRound) return;
    var status = data.approved ? 'ok' : 'fail';
    var label = data.approved ? 'Approved' : 'Needs Revision';
    addTimelineRowForRound(targetRound, data.approved ? 'phase' : 'phase',
        'Iteration #' + (data.iteration || '?'), label, status);
}

export function handleGraphTransition(data) {
    var targetRound = window.currentRound || (window.rounds.length > 0 ? window.rounds[window.rounds.length - 1] : null);
    if (!targetRound) return;
    addTimelineRowForRound(targetRound, 'phase', 'State',
        (data.fromState || '') + ' → ' + (data.toState || ''), 'ok');
}

export function handleRoundtableStart(data) {
    var targetRound = window.currentRound || (window.rounds.length > 0 ? window.rounds[window.rounds.length - 1] : null);
    if (!targetRound) return;
    addTimelineRowForRound(targetRound, 'phase', 'Roundtable',
        (data.participants || []).join(', ') + ' | ' + (data.rounds || 0) + ' rounds', 'running');
}

export function handleRoundMessage(data) {
    var targetRound = window.currentRound || (window.rounds.length > 0 ? window.rounds[window.rounds.length - 1] : null);
    if (!targetRound) return;
    addTimelineRowForRound(targetRound, 'phase', '💬 ' + (data.agent || ''),
        truncate(data.content || '', 60), 'ok');
}

export function handleTaskDelegate(data) {
    var targetRound = window.currentRound || (window.rounds.length > 0 ? window.rounds[window.rounds.length - 1] : null);
    if (!targetRound) return;
    addTimelineRowForRound(targetRound, 'phase', 'Task',
        (data.from || '') + ' → ' + (data.to || ''), 'running');
}

export function handleTaskEnd(data) {
    var targetRound = window.currentRound || (window.rounds.length > 0 ? window.rounds[window.rounds.length - 1] : null);
    if (!targetRound) return;
    addTimelineRowForRound(targetRound, 'phase', 'Task End',
        (data.agent || '') + ': ' + truncate(data.outputPreview || '', 50), 'ok');
}

function truncate(str, maxLen) {
    if (!str) return '';
    return str.length > maxLen ? str.substring(0, maxLen) + '...' : str;
}
```

- [ ] **Step 2: Wire new event types in chat.js**

In `chat.js`, add the new imports for debug handlers (update the existing debug.js import line):

```javascript
import { startRound, endRound, addTimelineRow, addTimelineRowForRound, clearDebug, toggleDebug, handlePipelineStart, handlePipelineStepStart, handlePipelineStepEnd, handleRoutingDecision, handleHandoffStart, updateRoundMetrics, updateRoundMetricsForRound, handleLoopStart, handleLoopEnd, handleLoopIterationResult, handleGraphTransition, handleRoundtableStart, handleRoundMessage, handleTaskDelegate, handleTaskEnd } from './modules/debug.js?v=2.6';
```

In the SSE event switch block (after the existing multi-agent events around line 388), add new cases:

```javascript
                        // ===== P6 ADVANCED PATTERN EVENTS =====

                        case 'loop_start':
                            handleLoopStart(payload);
                            break;
                        case 'loop_end':
                            handleLoopEnd(payload);
                            break;
                        case 'loop_iteration_result':
                            handleLoopIterationResult(payload);
                            break;
                        case 'graph_transition':
                            handleGraphTransition(payload);
                            break;
                        case 'graph_agent_call':
                            if (currentRound) {
                                addTimelineRow('phase', 'Agent @ ' + (payload.state || ''),
                                    payload.agent || '', 'running');
                            }
                            break;
                        case 'roundtable_start':
                            handleRoundtableStart(payload);
                            break;
                        case 'round_start':
                            if (currentRound) {
                                addTimelineRow('phase', 'Round ' + (payload.round || '?'), '', 'running');
                            }
                            break;
                        case 'round_end':
                            if (currentRound) {
                                addTimelineRow('phase', 'Round End', '', 'ok');
                            }
                            break;
                        case 'round_message':
                            handleRoundMessage(payload);
                            break;
                        case 'roundtable_summary':
                            if (currentRound) {
                                addTimelineRow('phase', 'Summary',
                                    truncate(payload.content || '', 80), 'ok');
                            }
                            break;
                        case 'task_delegate':
                            handleTaskDelegate(payload);
                            break;
                        case 'task_start':
                            if (currentRound) {
                                addTimelineRow('phase', 'Task: ' + (payload.agent || ''), '...', 'running');
                            }
                            break;
                        case 'task_end':
                            handleTaskEnd(payload);
                            break;
                        case 'task_aggregate':
                            if (currentRound) {
                                addTimelineRow('phase', 'Aggregate',
                                    (payload.totalTasks || 0) + ' tasks', 'ok');
                            }
                            break;
```

Also add the `truncate` helper in chat.js if not using the one from debug.js (or use the existing utils.js helper):

```javascript
function truncate(str, maxLen) {
    if (!str) return '';
    return str.length > maxLen ? str.substring(0, maxLen) + '...' : str;
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/scripts/modules/debug.js \
        src/main/resources/static/scripts/chat.js
git commit -m "feat: add P6 event handlers to debug panel and SSE dispatcher"
```

---

## Task 12: Compile, Verify, and Test

**Files:** No new files — verification only.

- [ ] **Step 1: Compile the project**

```bash
mvn clean compile
```

Expected: `BUILD SUCCESS`

Fix any compilation errors. Common issues:
- Missing imports in CompositeAgentFactory or AgentRuntimeFactory
- ReActAgent.builder() API mismatch in StateGraphRuntime
- AgentConfig field getter/setter naming mismatch with YAML keys

- [ ] **Step 2: Verify agent configurations load correctly**

Start the app:
```bash
export DASHSCOPE_API_KEY=your_key
mvn spring-boot:run
```

Check logs for:
- `Loaded N agents from config` where N includes the new P6 agents
- No YAML parsing errors

- [ ] **Step 3: Verify new agents appear in UI**

Open http://localhost:8080 in browser. Check that:
- "文案打磨" (copywriter-refiner) appears in agent list
- "订单履约" (order-fulfillment) appears in agent list
- "专家圆桌评审" (expert-roundtable) appears in agent list
- "报告生成流水线" (report-generator) appears in agent list
- "项目经理委派" (project-manager) appears in agent list

- [ ] **Step 4: Manual smoke test — Loop Pipeline**

Select "文案打磨" agent, send: "写一篇关于AI助手的产品介绍"
Expected: Writer produces content → Critic reviews → Loop iteration events in debug panel → Final polished output

- [ ] **Step 5: Manual smoke test — StateGraph**

Select "订单履约" agent, send: "提交订单"
Expected: State transitions in debug panel (CREATED → SUBMITTED → REVIEWING → ...)

- [ ] **Step 6: Manual smoke test — MsgHub**

Select "专家圆桌评审" agent, send: "评审方案：使用微服务架构重构现有单体应用"
Expected: Multiple experts speak in rounds → Summary from moderator

- [ ] **Step 7: Manual smoke test — Subagents Sequential**

Select "报告生成流水线" agent, send: "生成一份关于大语言模型在企业级应用中的报告"
Expected: Researcher → Analyst → Writer sequential task events

- [ ] **Step 8: Manual smoke test — Subagents Parallel**

Select "项目经理委派" agent, send: "开发一个基于AI的智能客服系统"
Expected: Three parallel task dispatch events → Aggregated results

- [ ] **Step 9: Final commit for any fixes**

```bash
git add -A
git commit -m "fix: address compilation and runtime issues from P6 smoke tests"
```

---

## Task 13: Update ROADMAP and CLAUDE.md

**Files:**
- Modify: `docs/ROADMAP.md` — mark P6 items as complete
- Modify: `CLAUDE.md` — add new agent types, patterns, and file paths

- [ ] **Step 1: Update ROADMAP.md**

Find the P6 section and update completion status for each item.

- [ ] **Step 2: Update CLAUDE.md**

Add the new patterns to the architecture documentation:
- New AgentType values
- New composite pipeline classes
- New agent configurations
- New SSE event types

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md CLAUDE.md
git commit -m "docs: update ROADMAP and CLAUDE.md for P6 completion"
```

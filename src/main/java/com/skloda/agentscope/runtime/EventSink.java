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

    // Event type constants
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

    // Typed convenience methods

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

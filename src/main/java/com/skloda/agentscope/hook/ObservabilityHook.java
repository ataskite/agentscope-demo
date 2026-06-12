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

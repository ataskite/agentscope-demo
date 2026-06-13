package com.skloda.agentscope.hook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ObservabilityHook's bridge behavior to EventSink.
 *
 * Note: In AgentScope 2.0, ObservabilityHook no longer implements the legacy Hook
 * interface and no longer receives automatic lifecycle events (PreCallEvent, etc.).
 * Automatic lifecycle events now flow through agent.streamEvents() + AgentEvent
 * processing. ObservabilityHook now serves purely as a bridge to EventSink for
 * manually-emitted multi-agent events (pipeline, routing, handoff, loop, etc.).
 */
class ObservabilityHookLifecycleTest {

    private ObservabilityHook hook;
    private List<Map<String, Object>> capturedEvents;

    @BeforeEach
    void setUp() {
        hook = new ObservabilityHook();
        capturedEvents = new ArrayList<>();
        hook.addConsumer((type, data) -> capturedEvents.add(data));
    }

    @Test
    void exposesEventSink() {
        assertNotNull(hook.getEventSink());
    }

    @Test
    void pipelineEvents() {
        hook.emitPipelineStart("p1", List.of("a1", "a2"));
        hook.emitPipelineStepStart("p1", 0, "agent1");
        hook.emitPipelineStepEnd("p1", 0, "agent1", 100L);
        hook.emitPipelineEnd("p1", 1, 200L);
        assertEquals(4, capturedEvents.size());
        assertEquals("pipeline_start", capturedEvents.get(0).get("type"));
        assertEquals("pipeline_step_start", capturedEvents.get(1).get("type"));
        assertEquals("pipeline_step_end", capturedEvents.get(2).get("type"));
        assertEquals("pipeline_end", capturedEvents.get(3).get("type"));
    }

    @Test
    void routingEvents() {
        hook.emitRoutingDecision("router", "agent1", "matched");
        hook.emitRoutingEnd("router", "agent1");
        assertEquals(2, capturedEvents.size());
        assertEquals("routing_decision", capturedEvents.get(0).get("type"));
        assertEquals("routing_end", capturedEvents.get(1).get("type"));
    }

    @Test
    void handoffEvents() {
        hook.emitHandoffStart("from", "to", "intent");
        hook.emitHandoffComplete("from", "to");
        assertEquals(2, capturedEvents.size());
        assertEquals("handoff_start", capturedEvents.get(0).get("type"));
        assertEquals("handoff_complete", capturedEvents.get(1).get("type"));
    }

    @Test
    void loopEvents() {
        hook.emitLoopStart(1);
        hook.emitLoopIterationResult(1, false, "needs work");
        hook.emitLoopEnd(3, true);
        assertEquals(3, capturedEvents.size());
        assertEquals("loop_start", capturedEvents.get(0).get("type"));
        assertEquals("loop_iteration_result", capturedEvents.get(1).get("type"));
        assertEquals("loop_end", capturedEvents.get(2).get("type"));
    }

    @Test
    void graphEvents() {
        hook.emitGraphAgentCall("REVIEWING", "reviewer");
        hook.emitGraphTransition("REVIEWING", "APPROVED", "decision");
        assertEquals(2, capturedEvents.size());
        assertEquals("graph_agent_call", capturedEvents.get(0).get("type"));
        assertEquals("graph_transition", capturedEvents.get(1).get("type"));
    }

    @Test
    void roundtableEvents() {
        hook.emitRoundtableStart("rt1", List.of("a1"), 3);
        hook.emitRoundStart(1);
        hook.emitRoundMessage("a1", "opinion");
        hook.emitRoundEnd(1);
        hook.emitRoundtableSummary("mod", "summary");
        assertEquals(5, capturedEvents.size());
        assertEquals("roundtable_start", capturedEvents.get(0).get("type"));
        assertEquals("round_start", capturedEvents.get(1).get("type"));
        assertEquals("round_message", capturedEvents.get(2).get("type"));
        assertEquals("round_end", capturedEvents.get(3).get("type"));
        assertEquals("roundtable_summary", capturedEvents.get(4).get("type"));
    }

    @Test
    void taskEvents() {
        hook.emitTaskDelegate("from", "to", "task");
        hook.emitTaskStart("agent1");
        hook.emitTaskEnd("agent1", "result");
        hook.emitTaskAggregate(3);
        assertEquals(4, capturedEvents.size());
        assertEquals("task_delegate", capturedEvents.get(0).get("type"));
        assertEquals("task_start", capturedEvents.get(1).get("type"));
        assertEquals("task_end", capturedEvents.get(2).get("type"));
        assertEquals("task_aggregate", capturedEvents.get(3).get("type"));
    }
}

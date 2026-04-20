package com.skloda.agentscope.hook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ObservabilityHookMultiAgentTest {

    private ObservabilityHook hook;
    private List<Map<String, Object>> capturedEvents;

    @BeforeEach
    void setUp() {
        hook = new ObservabilityHook();
        capturedEvents = new ArrayList<>();
        hook.addConsumer((type, data) -> capturedEvents.add(Map.of("type", type, "data", data)));
    }

    @Test
    void testEmitPipelineStart() {
        hook.emitPipelineStart("test-pipeline", List.of("agent1", "agent2"));
        assertEquals(1, capturedEvents.size());
        assertEquals("pipeline_start", capturedEvents.get(0).get("type"));
    }

    @Test
    void testEmitRoutingDecision() {
        hook.emitRoutingDecision("test-router", "agent1", "User asked about documents");
        assertEquals(1, capturedEvents.size());
        assertEquals("routing_decision", capturedEvents.get(0).get("type"));
    }

    @Test
    void testEmitHandoffStart() {
        hook.emitHandoffStart("support-agent", "sales-agent", "User wants to purchase");
        assertEquals(1, capturedEvents.size());
        assertEquals("handoff_start", capturedEvents.get(0).get("type"));
    }
}

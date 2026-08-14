package com.skloda.agentscope.harness;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HarnessRuntime} wiring and {@link com.skloda.agentscope.agent.HarnessConfig}.
 * <p>
 * Event-conversion coverage now lives in {@code AgentEventMapperTest}, since {@link HarnessRuntime}
 * delegates to the shared {@code AgentEventMapper} (it no longer has its own {@code convertEvent}
 * — that crude text-flattener was retired when the runtime switched to {@code streamEvents()}).
 */
class HarnessRuntimeTest {

    @Test
    void harnessConfigDefaultsToClawMode() {
        com.skloda.agentscope.agent.HarnessConfig config = new com.skloda.agentscope.agent.HarnessConfig();
        assertFalse(config.isBuilderMode());
        assertEquals("CLAW", config.getExecutionMode());
    }

    @Test
    void harnessConfigBuilderMode() {
        com.skloda.agentscope.agent.HarnessConfig config = new com.skloda.agentscope.agent.HarnessConfig();
        config.setExecutionMode("BUILDER");
        assertTrue(config.isBuilderMode());
    }
}

package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoopConfigTest {

    @Test
    void constructor_createsConfigWithDefaults() {
        LoopConfig config = new LoopConfig();

        assertNotNull(config);
        assertEquals(3, config.getMaxIterations());
        assertEquals("AUTO", config.getExitCondition());
    }

    @Test
    void builder_createsConfigWithCustomValues() {
        LoopConfig config = LoopConfig.builder()
                .maxIterations(5)
                .exitCondition("MANUAL")
                .build();

        assertEquals(5, config.getMaxIterations());
        assertEquals("MANUAL", config.getExitCondition());
    }

    @Test
    void builder_withDefaultExitCondition() {
        LoopConfig config = LoopConfig.builder()
                .maxIterations(10)
                .exitCondition("AUTO")
                .build();

        assertEquals(10, config.getMaxIterations());
        assertEquals("AUTO", config.getExitCondition());
    }

    @Test
    void allArgsConstructor_createsConfigWithAllValues() {
        LoopConfig config = new LoopConfig(7, "MANUAL");

        assertEquals(7, config.getMaxIterations());
        assertEquals("MANUAL", config.getExitCondition());
    }

    @Test
    void setter_canModifyMaxIterations() {
        LoopConfig config = new LoopConfig();
        config.setMaxIterations(15);

        assertEquals(15, config.getMaxIterations());
        assertEquals("AUTO", config.getExitCondition());
    }

    @Test
    void setter_canModifyExitCondition() {
        LoopConfig config = new LoopConfig();
        config.setExitCondition("MANUAL");

        assertEquals(3, config.getMaxIterations());
        assertEquals("MANUAL", config.getExitCondition());
    }

    @Test
    void exitCondition_auto() {
        LoopConfig config = LoopConfig.builder()
                .exitCondition("AUTO")
                .build();

        assertEquals("AUTO", config.getExitCondition());
    }

    @Test
    void exitCondition_manual() {
        LoopConfig config = LoopConfig.builder()
                .exitCondition("MANUAL")
                .build();

        assertEquals("MANUAL", config.getExitCondition());
    }
}

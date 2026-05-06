package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentConfigPlanAndMemoryTest {

    @Test
    void testPlanEnabledDefaultsToFalse() {
        AgentConfig config = new AgentConfig();
        assertFalse(config.isPlanEnabled());
    }

    @Test
    void testPlanEnabledCanBeSet() {
        AgentConfig config = new AgentConfig();
        config.setPlanEnabled(true);
        assertTrue(config.isPlanEnabled());
    }

    @Test
    void testLongTermMemoryDefaultsToNull() {
        AgentConfig config = new AgentConfig();
        assertNull(config.getLongTermMemory());
    }

    @Test
    void testLongTermMemoryConfigDefaults() {
        AgentConfig.LongTermMemoryConfig ltm = new AgentConfig.LongTermMemoryConfig();
        assertEquals("none", ltm.getType());
        assertEquals("STATIC_CONTROL", ltm.getMode());
        assertEquals("default_user", ltm.getUserId());
    }

    @Test
    void testLongTermMemoryConfigCanBeSet() {
        AgentConfig.LongTermMemoryConfig ltm = new AgentConfig.LongTermMemoryConfig();
        ltm.setType("bailian");
        ltm.setMode("AGENT_CONTROL");
        ltm.setUserId("user-123");

        assertEquals("bailian", ltm.getType());
        assertEquals("AGENT_CONTROL", ltm.getMode());
        assertEquals("user-123", ltm.getUserId());
    }

    @Test
    void testLongTermMemoryOnAgentConfig() {
        AgentConfig config = new AgentConfig();
        AgentConfig.LongTermMemoryConfig ltm = new AgentConfig.LongTermMemoryConfig();
        ltm.setType("bailian");
        config.setLongTermMemory(ltm);

        assertNotNull(config.getLongTermMemory());
        assertEquals("bailian", config.getLongTermMemory().getType());
    }
}

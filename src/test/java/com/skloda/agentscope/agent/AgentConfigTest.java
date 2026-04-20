package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class AgentConfigTest {
    @Test
    void testDefaultTypeIsSingle() {
        AgentConfig config = new AgentConfig();
        assertEquals(AgentType.SINGLE, config.getType());
    }

    @Test
    void testMultiAgentFields() {
        AgentConfig config = new AgentConfig();
        config.setType(AgentType.SEQUENTIAL);
        config.setSubAgents(List.of(
            SubAgentConfig.builder().agentId("agent1").description("First").build()
        ));
        config.setParallel(false);

        assertEquals(AgentType.SEQUENTIAL, config.getType());
        assertEquals(1, config.getSubAgents().size());
        assertFalse(config.getParallel());
    }
}

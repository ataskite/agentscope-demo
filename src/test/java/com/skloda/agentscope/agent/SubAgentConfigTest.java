package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SubAgentConfigTest {
    @Test
    void testBuilderPattern() {
        SubAgentConfig config = SubAgentConfig.builder()
            .agentId("search-expert")
            .description("Search expert")
            .build();
        assertEquals("search-expert", config.getAgentId());
        assertEquals("Search expert", config.getDescription());
    }
}

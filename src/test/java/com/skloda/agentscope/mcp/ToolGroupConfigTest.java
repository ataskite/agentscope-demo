package com.skloda.agentscope.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolGroupConfigTest {

    @Test
    void testBuilder() {
        ToolGroupConfig config = ToolGroupConfig.builder()
                .name("dev-tools")
                .description("Development tools group")
                .active(true)
                .build();

        assertEquals("dev-tools", config.getName());
        assertEquals("Development tools group", config.getDescription());
        assertTrue(config.getActive());
    }

    @Test
    void testBuilderDefaults() {
        ToolGroupConfig config = ToolGroupConfig.builder()
                .name("minimal")
                .build();

        assertEquals("minimal", config.getName());
        assertNull(config.getDescription());
        assertNull(config.getActive());
    }
}

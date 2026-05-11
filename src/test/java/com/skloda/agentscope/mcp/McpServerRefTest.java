package com.skloda.agentscope.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class McpServerRefTest {

    @Test
    void testBuilderWithEnableTools() {
        McpServerRef ref = McpServerRef.builder()
                .server("filesystem")
                .enableTools(List.of("read_file", "write_file"))
                .build();

        assertEquals("filesystem", ref.getServer());
        assertEquals(2, ref.getEnableTools().size());
        assertTrue(ref.getEnableTools().contains("read_file"));
        assertTrue(ref.getEnableTools().contains("write_file"));
        assertNull(ref.getDisableTools());
        assertNull(ref.getGroup());
    }

    @Test
    void testBuilderWithDisableTools() {
        McpServerRef ref = McpServerRef.builder()
                .server("search")
                .disableTools(List.of("dangerous_tool"))
                .build();

        assertEquals("search", ref.getServer());
        assertNull(ref.getEnableTools());
        assertEquals(1, ref.getDisableTools().size());
        assertTrue(ref.getDisableTools().contains("dangerous_tool"));
    }

    @Test
    void testBuilderWithGroup() {
        McpServerRef ref = McpServerRef.builder()
                .server("git")
                .group("dev-tools")
                .build();

        assertEquals("git", ref.getServer());
        assertEquals("dev-tools", ref.getGroup());
        assertNull(ref.getEnableTools());
        assertNull(ref.getDisableTools());
    }
}

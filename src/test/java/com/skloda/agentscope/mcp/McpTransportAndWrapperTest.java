package com.skloda.agentscope.mcp;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class McpTransportAndWrapperTest {

    @Test
    void mcpTransportValues() {
        assertEquals(3, McpTransport.values().length);
        assertNotNull(McpTransport.valueOf("STDIO"));
        assertNotNull(McpTransport.valueOf("SSE"));
        assertNotNull(McpTransport.valueOf("HTTP"));
    }

    @Test
    void mcpServersWrapperDefaultState() {
        McpServersWrapper wrapper = new McpServersWrapper();
        assertNotNull(wrapper.getServers());
        assertTrue(wrapper.getServers().isEmpty());
    }

    @Test
    void mcpServersWrapperSetServers() {
        McpServersWrapper wrapper = new McpServersWrapper();
        McpServerConfig config = new McpServerConfig();
        config.setName("test-server");
        config.setTransport(McpTransport.SSE);
        config.setUrl("http://localhost:9090/sse");

        wrapper.setServers(new ArrayList<>(java.util.List.of(config)));
        assertEquals(1, wrapper.getServers().size());
        assertEquals("test-server", wrapper.getServers().get(0).getName());
    }

    @Test
    void mcpServerConfigAllFields() {
        McpServerConfig config = new McpServerConfig();
        config.setName("full-server");
        config.setTransport(McpTransport.STDIO);
        config.setCommand("npx");
        config.setArgs(java.util.List.of("-y", "some-mcp-server"));
        config.setTimeout(30);
        config.setInitTimeout(10);
        config.setHeaders(java.util.Map.of("Authorization", "Bearer token"));
        config.setQueryParams(java.util.Map.of("key", "value"));

        assertEquals("full-server", config.getName());
        assertEquals(McpTransport.STDIO, config.getTransport());
        assertEquals("npx", config.getCommand());
        assertEquals(2, config.getArgs().size());
        assertEquals(30, config.getTimeout());
        assertEquals(10, config.getInitTimeout());
        assertEquals("Bearer token", config.getHeaders().get("Authorization"));
        assertEquals("value", config.getQueryParams().get("key"));
    }

    @Test
    void mcpServerRefProperties() {
        McpServerRef ref = McpServerRef.builder()
                .server("my-server")
                .enableTools(java.util.List.of("tool1"))
                .disableTools(java.util.List.of("tool2"))
                .group("group1")
                .build();
        assertEquals("my-server", ref.getServer());
        assertEquals(1, ref.getEnableTools().size());
        assertEquals(1, ref.getDisableTools().size());
        assertEquals("group1", ref.getGroup());
    }

    @Test
    void toolGroupConfigProperties() {
        ToolGroupConfig config = ToolGroupConfig.builder()
                .name("group1")
                .description("test group")
                .active(true)
                .build();
        assertEquals("group1", config.getName());
        assertEquals("test group", config.getDescription());
        assertTrue(config.getActive());
    }
}

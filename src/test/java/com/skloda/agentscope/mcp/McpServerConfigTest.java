package com.skloda.agentscope.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpServerConfigTest {

    @Test
    void testStdioConfigBuilder() {
        McpServerConfig config = McpServerConfig.builder()
                .name("local-mcp")
                .transport(McpTransport.STDIO)
                .command("npx")
                .args(List.of("-y", "@modelcontextprotocol/server-filesystem", "/tmp"))
                .timeout(30000)
                .initTimeout(10000)
                .build();

        assertEquals("local-mcp", config.getName());
        assertEquals(McpTransport.STDIO, config.getTransport());
        assertEquals("npx", config.getCommand());
        assertEquals(3, config.getArgs().size());
        assertEquals(30000, config.getTimeout());
        assertEquals(10000, config.getInitTimeout());
        assertNull(config.getUrl());
        assertNull(config.getHeaders());
        assertNull(config.getQueryParams());
    }

    @Test
    void testSseConfigBuilder() {
        McpServerConfig config = McpServerConfig.builder()
                .name("remote-sse")
                .transport(McpTransport.SSE)
                .url("http://localhost:3001/sse")
                .headers(Map.of("Authorization", "Bearer token123"))
                .queryParams(Map.of("version", "2"))
                .timeout(60000)
                .build();

        assertEquals("remote-sse", config.getName());
        assertEquals(McpTransport.SSE, config.getTransport());
        assertEquals("http://localhost:3001/sse", config.getUrl());
        assertEquals("Bearer token123", config.getHeaders().get("Authorization"));
        assertEquals("2", config.getQueryParams().get("version"));
        assertNull(config.getCommand());
        assertNull(config.getArgs());
    }

    @Test
    void testHttpConfigBuilder() {
        McpServerConfig config = McpServerConfig.builder()
                .name("remote-http")
                .transport(McpTransport.HTTP)
                .url("http://localhost:3001/mcp")
                .build();

        assertEquals("remote-http", config.getName());
        assertEquals(McpTransport.HTTP, config.getTransport());
        assertEquals("http://localhost:3001/mcp", config.getUrl());
        assertNull(config.getCommand());
        assertNull(config.getHeaders());
    }
}

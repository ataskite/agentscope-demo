package com.skloda.agentscope.mcp;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpServerConfig {
    private String name;
    private McpTransport transport;

    // StdIO fields
    private String command;
    private List<String> args;

    // SSE/HTTP fields
    private String url;
    private Map<String, String> headers;
    private Map<String, String> queryParams;

    // Common fields
    private Integer timeout;
    private Integer initTimeout;
}

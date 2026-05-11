package com.skloda.agentscope.mcp;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class McpServersWrapper {
    private List<McpServerConfig> servers = new ArrayList<>();
}

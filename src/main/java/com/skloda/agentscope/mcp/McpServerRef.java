package com.skloda.agentscope.mcp;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpServerRef {
    private String server;
    private List<String> enableTools;
    private List<String> disableTools;
    private String group;
}

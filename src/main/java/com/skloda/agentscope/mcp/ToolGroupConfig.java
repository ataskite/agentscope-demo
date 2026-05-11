package com.skloda.agentscope.mcp;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolGroupConfig {
    private String name;
    private String description;
    private Boolean active;
}

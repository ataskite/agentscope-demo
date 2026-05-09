package com.skloda.agentscope.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoopConfig {
    private int maxIterations = 3;
    private String exitCondition = "AUTO";
}
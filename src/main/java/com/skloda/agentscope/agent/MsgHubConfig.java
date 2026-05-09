package com.skloda.agentscope.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MsgHubConfig {
    private int rounds = 3;
    private String summaryRole = "MODERATOR";
}
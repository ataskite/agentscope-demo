package com.skloda.agentscope.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Lightweight DTO for session list API.
 */
@Setter
@Getter
public class SessionInfo {

    private String sessionId;
    private String agentId;
    private String agentName;
    private int messageCount;
    private String createdAt;
    private String lastAccessedAt;
    private String preview;

}

package com.skloda.agentscope.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * Request body for POST /chat/approve.
 */
@Setter
@Getter
public class ApprovalRequest {

    private String approvalId;
    private boolean approved;
    private String reason;
    private Map<String, Object> modifiedParams;
    private String sessionId;
}

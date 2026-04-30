package com.skloda.agentscope.model;

import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.ToolUseBlock;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a paused agent awaiting human approval before tool execution.
 */
@Value
@Builder
public class PendingApproval {

    String approvalId;
    String agentId;
    String sessionId;
    ReActAgent agent;
    ObservabilityHook hook;
    List<ToolUseBlock> pendingToolCalls;
    Instant createdAt;

    public List<Map<String, Object>> getToolCallsForDisplay() {
        return pendingToolCalls.stream()
                .map(t -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("id", t.getId() != null ? t.getId() : "");
                    map.put("name", t.getName() != null ? t.getName() : "");
                    map.put("input", t.getInput() != null ? t.getInput().toString() : "{}");
                    return map;
                })
                .toList();
    }
}

package com.skloda.agentscope.hook;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Hook that pauses agent execution before sensitive tool calls
 * by calling stopAgent() on PostReasoningEvent.
 *
 * Approval is checked at tool-level (approvalTools list) or agent-level (approvalRequired=true).
 */
public class ApprovalHook implements Hook {

    private final boolean approvalRequired;
    private final Set<String> approvalTools;
    private volatile boolean approvalTriggered = false;
    private List<ToolUseBlock> pendingToolUseBlocks = List.of();

    public ApprovalHook(boolean approvalRequired, List<String> approvalTools) {
        this.approvalRequired = approvalRequired;
        this.approvalTools = approvalTools != null ? new HashSet<>(approvalTools) : Set.of();
    }

    public boolean needsApproval() {
        return approvalRequired || !approvalTools.isEmpty();
    }

    @Override
    public int priority() {
        // Higher priority than default (100) so stopAgent fires before ObservabilityHook processes
        return 50;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostReasoningEvent e) {
            Msg reasoningMsg = e.getReasoningMessage();
            if (reasoningMsg != null && reasoningMsg.hasContentBlocks(ToolUseBlock.class)) {
                List<ToolUseBlock> toolCalls = reasoningMsg.getContentBlocks(ToolUseBlock.class);

                boolean needsApproval = toolCalls.stream().anyMatch(t ->
                        approvalRequired || approvalTools.contains(t.getName()));

                if (needsApproval) {
                    approvalTriggered = true;
                    pendingToolUseBlocks = List.copyOf(toolCalls);
                    e.stopAgent();
                }
            }
        }
        return Mono.just(event);
    }

    public boolean isApprovalTriggered() {
        return approvalTriggered;
    }

    public List<ToolUseBlock> getPendingToolUseBlocks() {
        return pendingToolUseBlocks;
    }

    public List<Map<String, Object>> getPendingToolCallsForSse() {
        return pendingToolUseBlocks.stream()
                .map(t -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", t.getId() != null ? t.getId() : "");
                    map.put("name", t.getName() != null ? t.getName() : "");
                    map.put("input", t.getInput() != null ? t.getInput().toString() : "{}");
                    return map;
                })
                .toList();
    }
}

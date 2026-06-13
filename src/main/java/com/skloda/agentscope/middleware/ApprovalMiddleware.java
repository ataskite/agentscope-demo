package com.skloda.agentscope.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.message.ToolUseBlock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Middleware that pauses agent execution before sensitive tool calls.
 *
 * <p>Replaces the deprecated {@code ApprovalHook} (which implemented the
 * 2.0-deprecated {@code io.agentscope.core.hook.Hook} interface). The old hook
 * intercepted {@code PostReasoningEvent} and called {@code stopAgent()}. The new
 * middleware intercepts the {@code onActing} phase -- {@link ActingInput#toolCalls()}
 * provides the same {@link ToolUseBlock} list as before.
 *
 * <p>Behavior:
 * <ul>
 *   <li>If approval is needed: sets {@code approvalTriggered}, stores the pending
 *       tool calls, and returns {@link Flux#empty()} to skip tool execution
 *       (the agent completes its stream without tool results and stops naturally).</li>
 *   <li>Otherwise: delegates to {@code next.apply(input)} normally.</li>
 * </ul>
 *
 * <p>Approval is checked at tool-level ({@code approvalTools} list) or
 * agent-level ({@code approvalRequired=true}).
 */
public class ApprovalMiddleware implements MiddlewareBase {

    private final boolean approvalRequired;
    private final Set<String> approvalTools;
    private volatile boolean approvalTriggered = false;
    private List<ToolUseBlock> pendingToolUseBlocks = List.of();

    public ApprovalMiddleware(boolean approvalRequired, List<String> approvalTools) {
        this.approvalRequired = approvalRequired;
        this.approvalTools = approvalTools != null ? new HashSet<>(approvalTools) : Set.of();
    }

    public boolean needsApproval() {
        return approvalRequired || !approvalTools.isEmpty();
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext ctx, ActingInput input,
                                      Function<ActingInput, Flux<AgentEvent>> next) {
        List<ToolUseBlock> toolCalls = input.toolCalls();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return next.apply(input);
        }

        boolean needsApproval = toolCalls.stream().anyMatch(t ->
                approvalRequired || approvalTools.contains(t.getName()));

        if (needsApproval) {
            approvalTriggered = true;
            pendingToolUseBlocks = List.copyOf(toolCalls);
            // Skip tool execution -- agent will complete stream without tool results
            return Flux.empty();
        }

        return next.apply(input);
    }

    // Default implementations for other phases -- just pass through
    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext ctx, AgentInput input,
                                     Function<AgentInput, Flux<AgentEvent>> next) {
        return next.apply(input);
    }

    @Override
    public Flux<AgentEvent> onReasoning(Agent agent, RuntimeContext ctx, ReasoningInput input,
                                         Function<ReasoningInput, Flux<AgentEvent>> next) {
        return next.apply(input);
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext ctx, ModelCallInput input,
                                         Function<ModelCallInput, Flux<AgentEvent>> next) {
        return next.apply(input);
    }

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String prompt) {
        return Mono.just(prompt);
    }

    // Public API for AgentRuntime (same surface as old ApprovalHook)
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
                    map.put("inputParams", t.getInput() != null ? t.getInput() : Map.of());
                    return map;
                })
                .toList();
    }
}

package com.skloda.agentscope.runtime;

import com.skloda.agentscope.middleware.ApprovalMiddleware;
import com.skloda.agentscope.hook.ObservabilityHook;
import com.skloda.agentscope.service.ApprovalService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runtime container for a single Agent interaction session.
 * Uses agent.streamEvents() for automatic lifecycle events (AgentScope 2.0)
 * and EventSink for manual multi-agent events.
 */
public class AgentRuntime implements StreamingAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);
    private static final AgentEventMapper eventMapper = new AgentEventMapper();

    @Getter
    private final ReActAgent agent;
    @Getter
    private final ObservabilityHook hook;
    private final ApprovalMiddleware approvalMiddleware;
    private final ApprovalService approvalService;
    private final String agentId;
    private final String sessionId;
    private final Runnable onClose;

    public AgentRuntime(ReActAgent agent, ObservabilityHook hook) {
        this(agent, hook, null, null, null, null, null);
    }

    public AgentRuntime(ReActAgent agent, ObservabilityHook hook, ApprovalMiddleware approvalMiddleware,
                        ApprovalService approvalService, String agentId) {
        this(agent, hook, approvalMiddleware, approvalService, agentId, null, null);
    }

    public AgentRuntime(ReActAgent agent, ObservabilityHook hook, ApprovalMiddleware approvalMiddleware,
                        ApprovalService approvalService, String agentId, String sessionId,
                        Runnable onClose) {
        this.agent = agent;
        this.hook = hook;
        this.approvalMiddleware = approvalMiddleware;
        this.approvalService = approvalService;
        this.agentId = agentId;
        this.sessionId = sessionId;
        this.onClose = onClose;
    }

    @Override
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        return stream(userMsg, false);
    }

    /**
     * Stream agent response as Flux.
     * Merges manual multi-agent events (from EventSink) with automatic agent lifecycle events.
     *
     * @param userMsg          the user message
     * @param isApprovalResume true if this is a resume after HITL approval
     */
    public Flux<Map<String, Object>> stream(Msg userMsg, boolean isApprovalResume) {
        log.debug("Starting stream for agent: {} (resume={})", agent.getName(), isApprovalResume);

        // Clean up stale pending tool calls from a previous (interrupted/timed-out) request
        // before starting a new one. When sessions are used, the AgentStateStore persists
        // conversation context across requests; if the previous call was interrupted after
        // the LLM emitted a ToolUseBlock but before the acting phase completed, that block
        // remains in context without a matching ToolResultBlock. On the next request,
        // AgentScope's enablePendingToolRecovery would auto-generate an error result
        // ("Auto-generated error result for pending tool call"), polluting the conversation.
        // We proactively remove such orphaned assistant messages to keep the context clean.
        if (!isApprovalResume) {
            clearStalePendingToolCalls();
        }

        // Manual multi-agent events from EventSink (pipeline, routing, handoff, etc.)
        Flux<Map<String, Object>> sinkEvents = hook.getEventSink().asFlux();

        Flux<Map<String, Object>> agentEvents;
        if (isApprovalResume) {
            agentEvents = createApprovalResumeStream(userMsg);
        } else {
            // Use handle() instead of map()+filter(): map() throws on null return,
            // but AgentEventMapper.apply() intentionally returns null for drop events.
            agentEvents = agent.streamEvents(userMsg)
                    .handle((event, sink) -> {
                        Map<String, Object> map = mapAgentEvent(event);
                        if (map != null && !map.isEmpty()) {
                            sink.next(map);
                        }
                    });
        }

        return Flux.merge(sinkEvents, agentEvents)
                .concatWith(Mono.fromCallable(() -> {
                    // On completion, check if approval was triggered
                    if (approvalMiddleware != null && approvalMiddleware.isApprovalTriggered()) {
                        return handleApprovalCompletion();
                    }
                    return Map.of("type", "done");
                }))
                .doOnCancel(this::close)
                .doOnComplete(() -> {
                    hook.getEventSink().complete();
                    this.close();
                })
                .doOnError(e -> {
                    log.error("Stream error for agent: {}", agent.getName(), e);
                    hook.getEventSink().complete();
                    this.close();
                });
    }

    /**
     * Approval resume path: re-invokes the agent via {@code streamEvents()} so that the
     * post-approval execution emits the same typed lifecycle events (thinking, tool_start,
     * tool_end, text, …) as the original stream. Previously this used {@code agent.call()},
     * which returned only the final {@link Msg} and dropped all intermediate events — leaving
     * the frontend with bare text after an approval.
     *
     * <p>If a non-null {@code userMsg} is supplied (the rejection-message path), it is fed to
     * the agent; otherwise the agent resumes its pending tool execution with an empty message
     * list (the framework's pending-tool-recovery path, enabled via
     * {@code enablePendingToolRecovery(true)} on the builder).
     */
    private Flux<Map<String, Object>> createApprovalResumeStream(Msg userMsg) {
        Flux<AgentEvent> resumeEvents = userMsg != null
                ? agent.streamEvents(userMsg)
                : agent.streamEvents(List.of());
        // Use handle() instead of map()+filter(): map() throws on null return,
        // but AgentEventMapper.apply() intentionally returns null for drop events.
        return resumeEvents.handle((event, sink) -> {
                    Map<String, Object> map = eventMapper.apply(event);
                    if (map != null && !map.isEmpty()) {
                        sink.next(map);
                    }
                });
    }

    /**
     * Build the approval completion event and register the pending approval.
     */
    private Map<String, Object> handleApprovalCompletion() {
        String approvalId = approvalService.registerPendingApproval(
                agent, hook, approvalMiddleware.getPendingToolUseBlocks(), agentId, sessionId);
        return Map.of(
                "type", "pending_approval",
                "approvalId", approvalId,
                "agentId", agentId != null ? agentId : "",
                "toolCalls", approvalMiddleware.getPendingToolCallsForSse(),
                "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * Convert an AgentEvent from streamEvents() into an SSE-compatible Map.
     * Delegates to {@link AgentEventMapper} which covers all 30 AgentEventType values.
     * Returns null for events that should be silently consumed.
     */
    private Map<String, Object> mapAgentEvent(AgentEvent event) {
        return eventMapper.apply(event);
    }

    /**
     * Detect and remove stale pending tool calls from the agent's conversation context.
     *
     * <p>A "stale pending tool call" is a {@link ToolUseBlock} in the last assistant message
     * that has no corresponding {@link ToolResultBlock} in context — meaning the previous
     * request was interrupted (timeout, cancellation, error) after the LLM emitted the tool
     * call but before the acting phase could execute it.
     *
     * <p>Without this cleanup, AgentScope's {@code enablePendingToolRecovery} would
     * auto-generate a synthetic error result on the next call, which pollutes the
     * conversation with misleading "[ERROR] Previous tool execution failed" messages.
     * Instead, we remove the orphaned assistant message entirely so the agent starts
     * fresh from the last clean state.
     *
     * <p>This is a no-op when there are no stale pending tool calls, when the agent has no
     * state (stateless mode with fresh store), or when the state accessor is unavailable.
     */
    private void clearStalePendingToolCalls() {
        try {
            io.agentscope.core.state.AgentState state = agent.getAgentState();
            if (state == null) {
                return;
            }
            List<Msg> context = state.contextMutable();
            if (context == null || context.isEmpty()) {
                return;
            }

            // Collect all tool result IDs already present in context
            Set<String> resolvedToolCallIds = context.stream()
                    .flatMap(m -> m.getContentBlocks(ToolResultBlock.class).stream())
                    .map(ToolResultBlock::getId)
                    .collect(Collectors.toSet());

            // Walk context backwards; remove trailing assistant messages whose ToolUseBlocks
            // have no matching results. We only remove the *last* contiguous run of orphaned
            // assistant messages — earlier messages with results are kept intact.
            List<Msg> toRemove = new ArrayList<>();
            for (int i = context.size() - 1; i >= 0; i--) {
                Msg msg = context.get(i);
                if (msg.getRole() != MsgRole.ASSISTANT) {
                    break; // Stop at the first non-assistant message (e.g., user or tool result)
                }
                List<ToolUseBlock> toolCalls = msg.getContentBlocks(ToolUseBlock.class);
                if (toolCalls.isEmpty()) {
                    break; // Assistant message without tool calls — not an orphan
                }
                boolean allUnresolved = toolCalls.stream()
                        .map(ToolUseBlock::getId)
                        .noneMatch(resolvedToolCallIds::contains);
                if (!allUnresolved) {
                    break; // At least one tool call was resolved — not stale
                }
                toRemove.add(msg);
            }

            if (toRemove.isEmpty()) {
                return;
            }

            // Remove stale messages (toRemove is in reverse order; remove from the end)
            for (Msg msg : toRemove) {
                context.remove(context.size() - 1);
            }

            List<String> staleToolNames = toRemove.stream()
                    .flatMap(m -> m.getContentBlocks(ToolUseBlock.class).stream())
                    .map(ToolUseBlock::getName)
                    .toList();
            log.warn("Cleared {} stale pending tool call(s) from previous request for agent: {} (tools: {})",
                    staleToolNames.size(), agent.getName(), staleToolNames);
        } catch (Exception e) {
            // Defensive: if anything goes wrong accessing agent state, log and continue
            // without cleanup. The framework's own pending-tool-recovery will still fire
            // as a fallback, so we degrade gracefully rather than breaking the request.
            log.debug("Could not clear stale pending tool calls for agent: {} ({})",
                    agent.getName(), e.getMessage());
        }
    }

    @Override
    public void close() {
        hook.reset();
        if (onClose != null) {
            onClose.run();
        }
        log.debug("AgentRuntime closed for agent: {}", agent.getName());
    }
}

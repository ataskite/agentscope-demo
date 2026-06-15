package com.skloda.agentscope.runtime;

import com.skloda.agentscope.middleware.ApprovalMiddleware;
import com.skloda.agentscope.hook.ObservabilityHook;
import com.skloda.agentscope.service.ApprovalService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

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

        // Manual multi-agent events from EventSink (pipeline, routing, handoff, etc.)
        Flux<Map<String, Object>> sinkEvents = hook.getEventSink().asFlux();

        Flux<Map<String, Object>> agentEvents;
        if (isApprovalResume) {
            agentEvents = createApprovalResumeStream(userMsg);
        } else {
            agentEvents = agent.streamEvents(userMsg)
                    .map(this::mapAgentEvent)
                    .filter(map -> map != null && !map.isEmpty());
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
        return resumeEvents.map(eventMapper::apply)
                .filter(map -> map != null && !map.isEmpty());
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

    @Override
    public void close() {
        hook.reset();
        if (onClose != null) {
            onClose.run();
        }
        log.debug("AgentRuntime closed for agent: {}", agent.getName());
    }
}

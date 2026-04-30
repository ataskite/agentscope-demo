package com.skloda.agentscope.runtime;

import com.skloda.agentscope.hook.ApprovalHook;
import com.skloda.agentscope.hook.ObservabilityHook;
import com.skloda.agentscope.service.ApprovalService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.*;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Runtime container for a single Agent interaction session.
 * Encapsulates Agent + Hook + Sink, providing a clean Flux interface.
 */
public class AgentRuntime implements StreamingAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    @Getter
    private final ReActAgent agent;
    @Getter
    private final ObservabilityHook hook;
    private final ApprovalHook approvalHook;
    private final ApprovalService approvalService;
    private final String agentId;
    private final String sessionId;
    private final Sinks.Many<Map<String, Object>> sink;
    private final BiConsumer<String, Map<String, Object>> hookBridge;
    private final Runnable onClose;

    public AgentRuntime(ReActAgent agent, ObservabilityHook hook) {
        this(agent, hook, null, null, null, null, null);
    }

    public AgentRuntime(ReActAgent agent, ObservabilityHook hook, ApprovalHook approvalHook,
                        ApprovalService approvalService, String agentId) {
        this(agent, hook, approvalHook, approvalService, agentId, null, null);
    }

    public AgentRuntime(ReActAgent agent, ObservabilityHook hook, ApprovalHook approvalHook,
                        ApprovalService approvalService, String agentId, String sessionId,
                        Runnable onClose) {
        this.agent = agent;
        this.hook = hook;
        this.approvalHook = approvalHook;
        this.approvalService = approvalService;
        this.agentId = agentId;
        this.sessionId = sessionId;
        this.onClose = onClose;
        this.sink = Sinks.many().multicast().onBackpressureBuffer();

        this.hookBridge = (type, data) -> {
            Map<String, Object> payload = new LinkedHashMap<>(data);
            payload.put("type", type);
            emit(payload);
        };
        hook.addConsumer(hookBridge);
    }

    @Override
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        return stream(userMsg, false);
    }

    /**
     * Stream agent response as Flux.
     * Merges hook events (timeline/metrics) with agent text stream.
     *
     * @param userMsg      the user message
     * @param isApprovalResume true if this is a resume after HITL approval
     */
    public Flux<Map<String, Object>> stream(Msg userMsg, boolean isApprovalResume) {
        log.debug("Starting stream for agent: {} (resume={})", agent.getName(), isApprovalResume);

        StreamOptions streamOptions = StreamOptions.builder()
                .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                .incremental(true)
                .includeReasoningResult(false)
                .build();

        Flux<Map<String, Object>> hookEvents = this.sink.asFlux()
                .doOnNext(payload -> log.debug("[hook -> stream] {}", payload));

        Flux<Map<String, Object>> textStream = Flux.create(fluxSink -> {
            if (isApprovalResume) {
                // Resume: call agent with null message to continue executing pending tools
                agent.call(userMsg != null ? userMsg : Msg.builder().build())
                        .subscribe(
                                msg -> {
                                    if (msg != null && msg.getContent() != null) {
                                        for (ContentBlock block : msg.getContent()) {
                                            if (block instanceof TextBlock tb) {
                                                String text = tb.getText();
                                                if (text != null && !text.isEmpty()) {
                                                    fluxSink.next(Map.of("type", "text", "content", text));
                                                }
                                            }
                                        }
                                    }
                                },
                                error -> {
                                    log.error("Resume stream error", error);
                                    fluxSink.error(error);
                                },
                                () -> completeStream(fluxSink, false)
                        );
            } else {
                agent.stream(userMsg, streamOptions)
                        .subscribe(
                                event -> handleStreamEvent(event, fluxSink),
                                error -> {
                                    log.error("Stream error", error);
                                    fluxSink.error(error);
                                },
                                () -> completeStream(fluxSink, false)
                        );
            }
        });

        return Flux.merge(hookEvents, textStream)
                .doOnCancel(this::close)
                .doOnComplete(() -> {
                    this.sink.tryEmitComplete();
                    this.close();
                })
                .doOnError(e -> {
                    log.error("Stream error for agent: {}", agent.getName(), e);
                    this.sink.tryEmitComplete();
                    this.close();
                });
    }

    private void completeStream(reactor.core.publisher.FluxSink<Map<String, Object>> fluxSink,
                                boolean skipClose) {
        log.debug("Stream completing for agent: {}", agent.getName());

        // Check if approval was triggered during this stream
        if (!skipClose && approvalHook != null && approvalHook.isApprovalTriggered()) {
            // Register the paused agent for later resume
            String approvalId = approvalService.registerPendingApproval(
                    agent, hook, approvalHook.getPendingToolUseBlocks(), agentId, sessionId);

            fluxSink.next(Map.of(
                    "type", "pending_approval",
                    "approvalId", approvalId,
                    "agentId", agentId != null ? agentId : "",
                    "toolCalls", approvalHook.getPendingToolCallsForSse(),
                    "timestamp", System.currentTimeMillis()
            ));
            fluxSink.next(Map.of("type", "done"));
            fluxSink.complete();
            return;
        }

        // Normal completion
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        fluxSink.next(Map.of("type", "done"));
        fluxSink.complete();
    }

    private void handleStreamEvent(Event event, reactor.core.publisher.FluxSink<Map<String, Object>> sink) {
        try {
            Msg msg = event.getMessage();
            if (msg != null && msg.getContent() != null) {
                for (ContentBlock block : msg.getContent()) {
                    if (block instanceof TextBlock tb) {
                        String text = tb.getText();
                        if (text != null && !text.isEmpty()) {
                            sink.next(Map.of("type", "text", "content", text));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error handling stream event", e);
        }
    }

    private void emit(Map<String, Object> payload) {
        Sinks.EmitResult result = sink.tryEmitNext(payload);
        if (result.isFailure()) {
            log.warn("Failed to emit event: {}", result);
        }
    }

    @Override
    public void close() {
        hook.removeConsumer(hookBridge);
        hook.reset();
        sink.tryEmitComplete();
        if (onClose != null) {
            onClose.run();
        }
        log.debug("AgentRuntime closed for agent: {}", agent.getName());
    }
}

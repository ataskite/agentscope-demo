package com.skloda.agentscope.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skloda.agentscope.hook.ApprovalHook;
import com.skloda.agentscope.hook.ObservabilityHook;
import com.skloda.agentscope.service.ApprovalService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.message.*;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Runtime container for a single Agent interaction session.
 * Encapsulates Agent + Hook + Sink, providing a clean Flux interface.
 */
public class AgentRuntime implements StreamingAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
                Hook stopAfterApprovedToolHook = new StopAfterApprovedToolHook();
                Runnable removeStopHook = addTemporaryHook(stopAfterApprovedToolHook);
                // Resume: call agent with null message to continue executing pending tools
                reactor.core.publisher.Mono<Msg> resumeCall = userMsg != null
                        ? agent.call(userMsg)
                        : agent.call();
                resumeCall
                        .doFinally(signalType -> removeStopHook.run())
                        .subscribe(
                                msg -> {
                                    if (msg != null && msg.getContent() != null) {
                                        for (ContentBlock block : msg.getContent()) {
                                            if (block instanceof TextBlock tb) {
                                                String text = tb.getText();
                                                if (text != null && !text.isEmpty()) {
                                                    fluxSink.next(Map.of("type", "text", "content", text));
                                                }
                                            } else if (block instanceof ToolResultBlock trb) {
                                                String text = formatToolResultBlock(trb);
                                                if (text != null && !text.isBlank()) {
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

    private Runnable addTemporaryHook(Hook hook) {
        List<Hook> hooks = agent.getHooks();
        if (hooks == null) {
            return () -> { };
        }
        hooks.add(hook);
        return () -> hooks.remove(hook);
    }

    private String formatToolResultBlock(ToolResultBlock resultBlock) {
        String resultText = resultBlock.getOutput().stream()
                .filter(TextBlock.class::isInstance)
                .map(TextBlock.class::cast)
                .map(TextBlock::getText)
                .reduce("", (left, right) -> left + right)
                .trim();
        if (resultText.isBlank()) {
            return "";
        }

        try {
            Map<String, Object> result = parseToolResultJson(resultText);
            if (Boolean.TRUE.equals(result.get("success"))) {
                if (result.containsKey("downloadUrl")) {
                    String summary = stringValue(result.get("summary"));
                    String riskLevel = stringValue(result.get("riskLevel"));
                    StringBuilder message = new StringBuilder("报告已生成：\n");
                    message.append("- [下载合同审查报告](").append(result.get("downloadUrl")).append(")\n");
                    if (!riskLevel.isBlank()) {
                        message.append("- 风险等级：").append(riskLevel).append("\n");
                    }
                    if (!summary.isBlank()) {
                        message.append("- 摘要：").append(summary).append("\n");
                    }
                    return message.toString();
                }
                if (result.containsKey("excelDownloadUrl") || result.containsKey("wordDownloadUrl")) {
                    StringBuilder message = new StringBuilder("文件已生成：\n");
                    if (result.containsKey("excelDownloadUrl")) {
                        message.append("- [Excel 文件](").append(result.get("excelDownloadUrl")).append(")\n");
                    }
                    if (result.containsKey("wordDownloadUrl")) {
                        message.append("- [Word 文件](").append(result.get("wordDownloadUrl")).append(")\n");
                    }
                    return message.toString();
                }
            }
        } catch (Exception ignored) {
            // Non-JSON tool outputs are displayed as plain text.
        }

        return resultText;
    }

    private Map<String, Object> parseToolResultJson(String resultText) throws Exception {
        String normalized = resultText.trim();
        try {
            JsonNode node = objectMapper.readTree(normalized);
            if (node.isTextual()) {
                normalized = node.asText().trim();
            }
        } catch (Exception ignored) {
            if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
                normalized = normalized.substring(1, normalized.length() - 1).trim();
            }
        }
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = objectMapper.readValue(normalized, String.class).trim();
        }
        return objectMapper.readValue(normalized, new TypeReference<>() { });
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : "";
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

    private static class StopAfterApprovedToolHook implements Hook {
        @Override
        public <T extends HookEvent> Mono<T> onEvent(T event) {
            if (event instanceof PostActingEvent postActingEvent) {
                postActingEvent.stopAgent();
            }
            return Mono.just(event);
        }
    }
}

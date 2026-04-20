package com.skloda.agentscope.runtime;

import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.pipeline.FanoutPipeline;
import io.agentscope.core.pipeline.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Sinks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Runtime container for pipeline-based composite agents (Sequential, Parallel).
 * Provides the same Flux<Map<String, Object>> interface as AgentRuntime,
 * but wraps a Pipeline instead of a ReActAgent.
 */
public class PipelineAgentRuntime implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PipelineAgentRuntime.class);

    private final String pipelineName;
    private final Pipeline<?> pipeline;
    private final ObservabilityHook hook;
    private final Sinks.Many<Map<String, Object>> sink;
    private final BiConsumer<String, Map<String, Object>> hookBridge;

    public PipelineAgentRuntime(String pipelineName, Pipeline<?> pipeline, ObservabilityHook hook) {
        this.pipelineName = pipelineName;
        this.pipeline = pipeline;
        this.hook = hook;
        this.sink = Sinks.many().multicast().onBackpressureBuffer();

        this.hookBridge = (type, data) -> {
            Map<String, Object> payload = new LinkedHashMap<>(data);
            payload.put("type", type);
            emit(payload);
        };
        hook.addConsumer(hookBridge);
    }

    /**
     * Stream pipeline response as Flux.
     * For SequentialPipeline: executes and extracts text from the final Msg.
     * For FanoutPipeline: executes and collects text from all agent responses.
     */
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        log.debug("Starting pipeline stream: {}", pipelineName);

        Flux<Map<String, Object>> pipelineStream = Flux.<Map<String, Object>>create(sink -> {
            try {
                hook.emitPipelineStart(pipelineName, List.of());
                long startNanos = System.nanoTime();

                Object result = pipeline.execute(userMsg).block();
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

                if (result instanceof Msg finalMsg) {
                    emitTextFromMsg(finalMsg, sink);
                } else if (result instanceof List<?> msgs) {
                    for (Object item : msgs) {
                        if (item instanceof Msg msg) {
                            emitTextFromMsg(msg, sink);
                        }
                    }
                }

                hook.emitPipelineEnd(pipelineName, 1, durationMs);
                sink.next(Map.of("type", "done"));
                sink.complete();
            } catch (Exception e) {
                log.error("Pipeline execution error: {}", pipelineName, e);
                sink.next(Map.of("type", "error", "message", e.getMessage()));
                sink.complete();
            }
        });

        return Flux.merge(this.sink.asFlux(), pipelineStream)
                .doOnCancel(this::close)
                .doOnComplete(this::close)
                .doOnError(e -> {
                    log.error("Pipeline stream error for: {}", pipelineName, e);
                    this.close();
                });
    }

    /**
     * Stream FanoutPipeline with real-time streaming support.
     */
    public Flux<Map<String, Object>> streamWithEvents(Msg userMsg, FanoutPipeline fanoutPipeline) {
        log.debug("Starting fanout pipeline stream: {}", pipelineName);

        StreamOptions streamOptions = StreamOptions.builder()
                .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                .incremental(true)
                .includeReasoningResult(true)
                .build();

        Flux<Map<String, Object>> hookEvents = this.sink.asFlux();

        Flux<Map<String, Object>> pipelineStream = Flux.<Map<String, Object>>create(sink -> {
            try {
                hook.emitPipelineStart(pipelineName, List.of());
                long startNanos = System.nanoTime();

                fanoutPipeline.stream(userMsg, streamOptions)
                        .subscribe(
                                event -> handleStreamEvent(event, sink),
                                error -> {
                                    log.error("Fanout stream error", error);
                                    sink.next(Map.of("type", "error", "message", error.getMessage()));
                                    sink.complete();
                                },
                                () -> {
                                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                                    hook.emitPipelineEnd(pipelineName, 1, durationMs);
                                    log.debug("Fanout stream completed for: {}", pipelineName);
                                    sink.next(Map.of("type", "done"));
                                    sink.complete();
                                }
                        );
            } catch (Exception e) {
                log.error("Fanout pipeline setup error: {}", pipelineName, e);
                sink.next(Map.of("type", "error", "message", e.getMessage()));
                sink.complete();
            }
        });

        return Flux.merge(hookEvents, pipelineStream)
                .doOnCancel(this::close)
                .doOnComplete(this::close)
                .doOnError(e -> {
                    log.error("Fanout stream error for: {}", pipelineName, e);
                    this.close();
                });
    }

    private void emitTextFromMsg(Msg msg, FluxSink<Map<String, Object>> sink) {
        if (msg == null || msg.getContent() == null) return;
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb) {
                String text = tb.getText();
                if (text != null && !text.isEmpty()) {
                    sink.next(Map.of("type", "text", "content", text));
                }
            }
        }
    }

    private void handleStreamEvent(Event event, FluxSink<Map<String, Object>> sink) {
        try {
            Msg msg = event.getMessage();
            if (msg != null && msg.getContent() != null) {
                for (ContentBlock block : msg.getContent()) {
                    if (block instanceof TextBlock tb && !event.isLast()) {
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
        log.debug("PipelineAgentRuntime closed for: {}", pipelineName);
    }

    public String getPipelineName() {
        return pipelineName;
    }

    public ObservabilityHook getHook() {
        return hook;
    }
}

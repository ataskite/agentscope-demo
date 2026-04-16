package com.skloda.agentscope.runtime;

import com.skloda.agentscope.hook.ObservabilityHook;
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
public class AgentRuntime implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    /**
     * -- GETTER --
     *  Get the underlying Agent instance.
     */
    @Getter
    private final ReActAgent agent;
    /**
     * -- GETTER --
     *  Get the Hook instance.
     */
    @Getter
    private final ObservabilityHook hook;
    private final Sinks.Many<Map<String, Object>> sink;
    private final BiConsumer<String, Map<String, Object>> hookBridge;

    public AgentRuntime(ReActAgent agent, ObservabilityHook hook) {
        this.agent = agent;
        this.hook = hook;
        this.sink = Sinks.many().multicast().onBackpressureBuffer();

        // Bridge Hook events to Sink
        this.hookBridge = (type, data) -> {
            Map<String, Object> payload = new LinkedHashMap<>(data);
            payload.put("type", type);
            emit(payload);
        };
        hook.addConsumer(hookBridge);
    }

    /**
     * Stream agent response as Flux.
     * Merges hook events (timeline/metrics) with agent text stream.
     */
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        log.debug("Starting stream for agent: {}", agent.getName());

        StreamOptions streamOptions = StreamOptions.builder()
                .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                .incremental(true)
                .includeReasoningResult(true)
                .build();

        // Hook events flux (from ObservabilityHook)
        Flux<Map<String, Object>> hookEvents = this.sink.asFlux()
                .doOnNext(payload -> log.debug("[hook -> stream] {}", payload));

        // Agent text stream flux
        Flux<Map<String, Object>> textStream = Flux.create(sink -> {
            agent.stream(userMsg, streamOptions)
                    .subscribe(
                            event -> handleStreamEvent(event, sink),
                            error -> {
                                log.error("Stream error", error);
                                sink.error(error);
                            },
                            () -> {
                                log.debug("Text stream completed for agent: {}", agent.getName());
                                // Send done event before completing
                                sink.next(Map.of("type", "done"));
                                sink.complete();
                            }
                    );
        });

        // Merge both streams
        return Flux.merge(hookEvents, textStream)
                .doOnCancel(this::close)
                .doOnComplete(this::close)
                .doOnError(e -> {
                    log.error("Stream error for agent: {}", agent.getName(), e);
                    this.close();
                });
    }

    private void handleStreamEvent(Event event, reactor.core.publisher.FluxSink<Map<String, Object>> sink) {
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

    /**
     * Cleanup resources: remove hook consumer, reset hook state, complete sink.
     */
    @Override
    public void close() {
        hook.removeConsumer(hookBridge);
        hook.reset();
        sink.tryEmitComplete();
        log.debug("AgentRuntime closed for agent: {}", agent.getName());
    }
}

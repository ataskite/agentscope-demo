package com.skloda.agentscope.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Runtime for agents configured with structured output.
 * Uses agent.call(msg, Class) instead of streaming to get typed results.
 * Emits text content + structured_data events.
 */
public class StructuredOutputAgentRuntime implements StreamingAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutputAgentRuntime.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Getter
    private final ReActAgent agent;
    @Getter
    private final ObservabilityHook hook;
    private final String structuredOutputClassName;
    private final Sinks.Many<Map<String, Object>> sink;
    private final BiConsumer<String, Map<String, Object>> hookBridge;

    public StructuredOutputAgentRuntime(ReActAgent agent, ObservabilityHook hook,
                                         String structuredOutputClassName) {
        this.agent = agent;
        this.hook = hook;
        this.structuredOutputClassName = structuredOutputClassName;
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
        log.debug("Starting structured output stream for agent: {} (schema={})",
                agent.getName(), structuredOutputClassName);

        Flux<Map<String, Object>> hookEvents = this.sink.asFlux();

        Flux<Map<String, Object>> resultStream = Flux.create(fluxSink -> {
            try {
                Class<?> schemaClass = Class.forName(structuredOutputClassName);

                Msg response = agent.call(userMsg, schemaClass).block();

                if (response != null) {
                    // Emit text content
                    if (response.getContent() != null) {
                        for (ContentBlock block : response.getContent()) {
                            if (block instanceof TextBlock tb) {
                                String text = tb.getText();
                                if (text != null && !text.isEmpty()) {
                                    fluxSink.next(Map.of("type", "text", "content", text));
                                }
                            }
                        }
                    }

                    // Emit structured data
                    Object structuredData = response.getStructuredData(schemaClass);
                    if (structuredData != null) {
                        String json = objectMapper.writeValueAsString(structuredData);
                        fluxSink.next(Map.of(
                                "type", "structured_data",
                                "schemaClass", structuredOutputClassName,
                                "data", json
                        ));
                    }
                }

                fluxSink.next(Map.of("type", "done"));
                fluxSink.complete();
            } catch (ClassNotFoundException e) {
                log.error("Schema class not found: {}", structuredOutputClassName, e);
                fluxSink.next(Map.of("type", "error", "message",
                        "Schema class not found: " + structuredOutputClassName));
                fluxSink.next(Map.of("type", "done"));
                fluxSink.complete();
            } catch (Exception e) {
                log.error("Structured output error", e);
                fluxSink.next(Map.of("type", "error", "message",
                        e.getMessage() != null ? e.getMessage() : "Unknown error"));
                fluxSink.next(Map.of("type", "done"));
                fluxSink.complete();
            }
        });

        return Flux.merge(hookEvents, resultStream)
                .doOnComplete(() -> {
                    this.sink.tryEmitComplete();
                    this.close();
                })
                .doOnCancel(this::close)
                .doOnError(e -> {
                    log.error("Structured output stream error", e);
                    this.sink.tryEmitComplete();
                    this.close();
                });
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
        log.debug("StructuredOutputAgentRuntime closed for agent: {}", agent.getName());
    }
}

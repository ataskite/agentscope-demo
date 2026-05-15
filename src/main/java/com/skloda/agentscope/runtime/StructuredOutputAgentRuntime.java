package com.skloda.agentscope.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
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
    private static final int DEFAULT_MAX_REPAIR_ATTEMPTS = 1;

    @Getter
    private final ReActAgent agent;
    @Getter
    private final ObservabilityHook hook;
    private final String structuredOutputClassName;
    private final StructuredOutputValidator validator;
    private final int maxRepairAttempts;
    private final Sinks.Many<Map<String, Object>> sink;
    private final BiConsumer<String, Map<String, Object>> hookBridge;

    public StructuredOutputAgentRuntime(ReActAgent agent, ObservabilityHook hook,
                                         String structuredOutputClassName) {
        this(agent, hook, structuredOutputClassName, new StructuredOutputValidator(), DEFAULT_MAX_REPAIR_ATTEMPTS);
    }

    StructuredOutputAgentRuntime(ReActAgent agent, ObservabilityHook hook,
                                 String structuredOutputClassName,
                                 StructuredOutputValidator validator,
                                 int maxRepairAttempts) {
        this.agent = agent;
        this.hook = hook;
        this.structuredOutputClassName = structuredOutputClassName;
        this.validator = validator;
        this.maxRepairAttempts = maxRepairAttempts;
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

                Msg response = null;
                Object structuredData = null;
                StructuredOutputValidator.ValidationResult validation = null;

                for (int attempt = 0; attempt <= maxRepairAttempts; attempt++) {
                    Msg request = attempt == 0
                            ? userMsg
                            : buildRepairMessage(schemaClass, validation);
                    response = agent.call(request, schemaClass).block();
                    emitTextContent(response, fluxSink);

                    structuredData = extractStructuredData(response, schemaClass);
                    validation = validator.validate(structuredData);
                    if (validation.valid()) {
                        fluxSink.next(Map.of(
                                "type", "structured_validation_passed",
                                "schemaClass", structuredOutputClassName,
                                "attempt", attempt
                        ));
                        break;
                    }

                    fluxSink.next(Map.of(
                            "type", "structured_validation_failed",
                            "schemaClass", structuredOutputClassName,
                            "attempt", attempt,
                            "missingFields", validation.missingFields(),
                            "message", validation.message()
                    ));
                    if (attempt < maxRepairAttempts) {
                        fluxSink.next(Map.of(
                                "type", "structured_repair_start",
                                "schemaClass", structuredOutputClassName,
                                "attempt", attempt + 1,
                                "missingFields", validation.missingFields()
                        ));
                    }
                }

                if (validation == null || validation.invalid()) {
                    fluxSink.next(Map.of("type", "error", "message",
                            validation != null ? validation.message() : "Structured output validation failed."));
                } else if (structuredData != null) {
                    String json = objectMapper.writeValueAsString(structuredData);
                    fluxSink.next(Map.of(
                            "type", "structured_data",
                            "schemaClass", structuredOutputClassName,
                            "data", json
                    ));
                }

                fluxSink.next(Map.of("type", "done"));
                close();
                fluxSink.complete();
            } catch (ClassNotFoundException e) {
                log.error("Schema class not found: {}", structuredOutputClassName, e);
                fluxSink.next(Map.of("type", "error", "message",
                        "Schema class not found: " + structuredOutputClassName));
                fluxSink.next(Map.of("type", "done"));
                close();
                fluxSink.complete();
            } catch (Exception e) {
                log.error("Structured output error", e);
                fluxSink.next(Map.of("type", "error", "message",
                        e.getMessage() != null ? e.getMessage() : "Unknown error"));
                fluxSink.next(Map.of("type", "done"));
                close();
                fluxSink.complete();
            }
        });

        return Flux.merge(hookEvents, resultStream)
                .doOnCancel(this::close);
    }

    private void emitTextContent(Msg response, reactor.core.publisher.FluxSink<Map<String, Object>> fluxSink) {
        if (response == null || response.getContent() == null) {
            return;
        }
        for (ContentBlock block : response.getContent()) {
            if (block instanceof TextBlock tb) {
                String text = tb.getText();
                if (text != null && !text.isEmpty()) {
                    fluxSink.next(Map.of("type", "text", "content", text));
                }
            }
        }
    }

    private Object extractStructuredData(Msg response, Class<?> schemaClass) {
        if (response == null || !response.hasStructuredData()) {
            return null;
        }
        return response.getStructuredData(schemaClass);
    }

    private Msg buildRepairMessage(Class<?> schemaClass,
                                   StructuredOutputValidator.ValidationResult validation) {
        String missing = validation != null
                ? String.join(", ", validation.missingFields())
                : "unknown";
        String content = """
                The previous structured output for schema %s failed validation.
                Missing or invalid fields: %s.
                Return the same schema again with every required field completed.
                Preserve correct values from the previous extraction when possible.
                """.formatted(schemaClass.getSimpleName(), missing);
        return Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .textContent(content)
                .build();
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

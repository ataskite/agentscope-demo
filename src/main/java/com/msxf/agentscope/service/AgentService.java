package com.msxf.agentscope.service;

import com.msxf.agentscope.agent.AgentFactory;
import com.msxf.agentscope.hook.ObservabilityHook;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.agent.Event;
import io.agentscope.core.message.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Agent service that uses the AgentScope Hook system for lifecycle observability
 * and streaming for content delivery.
 *
 * Hook events → lifecycle timeline (agent_start, llm_start/end, tool_start/end, agent_end)
 * Stream events → content delivery (text blocks for chat display, done signal)
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AgentFactory agentFactory;

    public AgentService(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    public void streamToEmitter(String agentId, String message, String filePath, String fileName, SseEmitter emitter) {
        // Create a fresh ObservabilityHook for this request
        ObservabilityHook hook = new ObservabilityHook();

        // Wire hook events → SSE emitter
        BiConsumer<String, Map<String, Object>> sseConsumer = (type, data) -> {
            try {
                sendEvent(emitter, type, data);
            } catch (Exception e) {
                log.warn("Failed to send SSE event {}: {}", type, e.getMessage());
            }
        };
        hook.addConsumer(sseConsumer);

        // Create fresh agent with the observability hook
        ReActAgent agent = agentFactory.createAgent(agentId, hook);

        String actualMessage = message;
        if (filePath != null && !filePath.isBlank()) {
            String fileInfo = String.format("[用户上传了文件: %s, 路径: %s]\n\n", fileName, filePath);
            actualMessage = fileInfo + message;
        }

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(actualMessage).build())
                .build();

        // Stream options: only need text content from stream;
        // lifecycle events come from the hook
        StreamOptions streamOptions = StreamOptions.builder()
                .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                .incremental(true)
                .includeReasoningResult(true)
                .build();

        agent.stream(userMsg, streamOptions)
                .subscribe(
                        event -> {
                            try {
                                handleStreamEvent(event, emitter);
                            } catch (Exception e) {
                                log.error("Error handling stream event", e);
                            }
                        },
                        error -> {
                            log.error("Stream error", error);
                            try {
                                sendEvent(emitter, "error", Map.of("message", error.getMessage() != null ? error.getMessage() : "Unknown error"));
                            } catch (Exception e) {
                                log.error("Error sending error event", e);
                            }
                            cleanup(hook, sseConsumer);
                            emitter.completeWithError(error);
                        },
                        () -> {
                            try {
                                sendEvent(emitter, "done", Map.of());
                            } catch (Exception e) {
                                log.error("Error sending done event", e);
                            }
                            cleanup(hook, sseConsumer);
                            emitter.complete();
                        }
                );
    }

    /**
     * Handle stream events - only for text content delivery to the chat area.
     * All lifecycle events (LLM timing, tool timing, etc.) are handled by the Hook.
     */
    private void handleStreamEvent(Event event, SseEmitter emitter) throws Exception {
        Msg msg = event.getMessage();
        if (msg == null || msg.getContent() == null) return;

        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb && !event.isLast()) {
                String text = tb.getText();
                if (text != null && !text.isEmpty()) {
                    sendEvent(emitter, "text", Map.of("content", text));
                }
            }
        }
    }

    private void cleanup(ObservabilityHook hook, BiConsumer<String, Map<String, Object>> consumer) {
        hook.removeConsumer(consumer);
        hook.reset();
    }

    private void sendEvent(SseEmitter emitter, String type, Map<String, Object> data) throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>(data);
        payload.put("type", type);
        String json = objectMapper.writeValueAsString(payload);
        emitter.send(SseEmitter.event().name("message").data(json));
    }
}

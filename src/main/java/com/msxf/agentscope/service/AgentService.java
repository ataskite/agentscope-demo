package com.msxf.agentscope.service;

import com.msxf.agentscope.agent.AgentFactory;
import com.msxf.agentscope.hook.ObservabilityHook;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.agent.Event;
import io.agentscope.core.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Agent service that bridges AgentScope's reactive stream and
 * ObservabilityHook events into a single Flux for SSE delivery.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentFactory agentFactory;

    public AgentService(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                       String filePath, String fileName) {
        return Flux.create(sink -> {
            // 1. Create Hook, bridge events to sink
            ObservabilityHook hook = new ObservabilityHook();
            BiConsumer<String, Map<String, Object>> sseConsumer = (type, data) -> {
                Map<String, Object> payload = new LinkedHashMap<>(data);
                payload.put("type", type);
                sink.next(payload);
            };
            hook.addConsumer(sseConsumer);

            try {
                // 2. Create Agent with hook
                ReActAgent agent = agentFactory.createAgent(agentId, hook);

                // 3. Build message (file attachment logic unchanged)
                String actualMessage = message;
                if (filePath != null && !filePath.isBlank()) {
                    String fileInfo = String.format("[用户上传了文件: %s, 路径: %s]\n\n", fileName, filePath);
                    actualMessage = fileInfo + message;
                }

                Msg userMsg = Msg.builder()
                        .role(MsgRole.USER)
                        .content(TextBlock.builder().text(actualMessage).build())
                        .build();

                // 4. Subscribe to AgentScope's reactive stream
                StreamOptions streamOptions = StreamOptions.builder()
                        .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                        .incremental(true)
                        .includeReasoningResult(true)
                        .build();

                agent.stream(userMsg, streamOptions)
                        .subscribe(
                                event -> {
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
                                },
                                error -> {
                                    log.error("Stream error", error);
                                    sink.error(error);
                                },
                                () -> {
                                    hook.removeConsumer(sseConsumer);
                                    hook.reset();
                                    sink.complete();
                                }
                        );
            } catch (Exception e) {
                log.error("Error creating agent stream", e);
                hook.removeConsumer(sseConsumer);
                hook.reset();
                sink.error(e);
            }
        });
    }
}

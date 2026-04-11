package com.msxf.agentscope.service;

import com.msxf.agentscope.agent.AgentFactory;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.agent.Event;
import io.agentscope.core.message.*;
import io.agentscope.core.model.ChatUsage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AgentFactory agentFactory;
    private final ConcurrentHashMap<String, ReActAgent> agents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> toolCallStartTimes = new ConcurrentHashMap<>();

    public AgentService(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    public ReActAgent getAgent(String agentId) {
        return agents.computeIfAbsent(agentId, agentFactory::createAgent);
    }

    public void streamToEmitter(String agentId, String message, String filePath, String fileName, SseEmitter emitter) {
        ReActAgent agent = getAgent(agentId);

        String actualMessage = message;
        if (filePath != null && !filePath.isBlank()) {
            String fileInfo = String.format("[用户上传了文件: %s, 路径: %s]\n\n", fileName, filePath);
            actualMessage = fileInfo + message;
        }

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(actualMessage).build())
                .build();

        StreamOptions streamOptions = StreamOptions.builder()
                .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                .incremental(true)
                .includeReasoningResult(false)
                .build();

        agent.stream(userMsg, streamOptions)
                .subscribe(
                        event -> {
                            try {
                                handleEvent(event, emitter);
                            } catch (Exception e) {
                                log.error("Error handling stream event", e);
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            log.error("Stream error", error);
                            try {
                                sendEvent(emitter, "error", Map.of("message", error.getMessage() != null ? error.getMessage() : "Unknown error"));
                            } catch (Exception e) {
                                log.error("Error sending error event", e);
                            }
                            emitter.completeWithError(error);
                        },
                        () -> {
                            try {
                                Map<String, Object> doneData = new java.util.LinkedHashMap<>();
                                doneData.put("type", "done");
                                doneData.put("totalLlmCalls", llmCallCount);
                                doneData.put("toolCallsRemaining", toolCallStartTimes.size());
                                String json = objectMapper.writeValueAsString(doneData);
                                emitter.send(SseEmitter.event().name("message").data(json));
                            } catch (Exception e) {
                                log.error("Error sending done event", e);
                                emitter.completeWithError(e);
                                return;
                            }
                            emitter.complete();
                            llmCallCount = 0;
                            toolCallStartTimes.clear();
                        }
                );
    }

    private int llmCallCount = 0;

    private void handleEvent(Event event, SseEmitter emitter) throws Exception {
        Msg msg = event.getMessage();
        if (msg == null || msg.getContent() == null) {
            return;
        }

        for (ContentBlock block : msg.getContent()) {
            if (block instanceof ThinkingBlock tb) {
                String thinking = tb.getThinking();
                if (thinking != null && !thinking.isEmpty()) {
                    sendEvent(emitter, "thinking", Map.of("content", thinking));
                }
            } else if (block instanceof TextBlock tb) {
                String text = tb.getText();
                if (text != null && !text.isEmpty()) {
                    sendEvent(emitter, "text", Map.of("content", text));
                }
            } else if (block instanceof ToolUseBlock tub) {
                String toolId = tub.getId();
                toolCallStartTimes.put(toolId, System.currentTimeMillis());
                sendEvent(emitter, "tool_call", Map.of(
                        "id", toolId != null ? toolId : "",
                        "name", tub.getName() != null ? tub.getName() : "",
                        "params", tub.getInput() != null ? tub.getInput().toString() : "{}",
                        "timestamp", System.currentTimeMillis()
                ));
            } else if (block instanceof ToolResultBlock trb) {
                String resultText = trb.getOutput() != null
                        ? trb.getOutput().stream()
                        .filter(o -> o instanceof TextBlock)
                        .map(o -> ((TextBlock) o).getText())
                        .reduce("", (a, b) -> a + b)
                        : "";
                String toolId = trb.getId();
                Long startTime = toolId != null ? toolCallStartTimes.remove(toolId) : null;
                long durationMs = startTime != null ? System.currentTimeMillis() - startTime : -1;

                String resultPreview = resultText.length() > 200
                        ? resultText.substring(0, 200) + "..."
                        : resultText;

                sendEvent(emitter, "tool_result", Map.of(
                        "id", toolId != null ? toolId : "",
                        "name", trb.getName() != null ? trb.getName() : "",
                        "result", resultText,
                        "result_preview", resultPreview,
                        "duration_ms", durationMs,
                        "timestamp", System.currentTimeMillis()
                ));
            }
        }

        if (event.isLast()) {
            ChatUsage usage = msg.getChatUsage();
            if (usage != null) {
                llmCallCount++;
                sendEvent(emitter, "usage", Map.of(
                        "inputTokens", usage.getInputTokens(),
                        "outputTokens", usage.getOutputTokens(),
                        "totalTokens", usage.getTotalTokens(),
                        "time", usage.getTime(),
                        "callNumber", llmCallCount
                ));
            }
        }
    }

    private void sendEvent(SseEmitter emitter, String type, Map<String, Object> data) throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>(data);
        payload.put("type", type);
        String json = objectMapper.writeValueAsString(payload);
        emitter.send(SseEmitter.event().name("message").data(json));
    }
}

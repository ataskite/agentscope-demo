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

    public AgentService(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    public ReActAgent getAgent(String agentId) {
        return agents.computeIfAbsent(agentId, agentFactory::createAgent);
    }

    public void streamToEmitter(String agentId, String message, String filePath, String fileName, SseEmitter emitter) {
        final ConcurrentHashMap<String, Long> toolCallStartTimes = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, Boolean> sentToolCalls = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, Boolean> sentToolResults = new ConcurrentHashMap<>();
        final int[] llmCallCount = {0};

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
                .includeReasoningResult(true)
                .build();

        agent.stream(userMsg, streamOptions)
                .subscribe(
                        event -> {
                            try {
                                handleEvent(event, emitter, toolCallStartTimes, sentToolCalls, sentToolResults, llmCallCount);
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
                                doneData.put("totalLlmCalls", llmCallCount[0]);
                                doneData.put("toolCallsRemaining", toolCallStartTimes.size());
                                String json = objectMapper.writeValueAsString(doneData);
                                emitter.send(SseEmitter.event().name("message").data(json));
                            } catch (Exception e) {
                                log.error("Error sending done event", e);
                                emitter.completeWithError(e);
                                return;
                            }
                            emitter.complete();
                        }
                );
    }

    private void handleEvent(Event event, SseEmitter emitter, ConcurrentHashMap<String, Long> toolCallStartTimes, ConcurrentHashMap<String, Boolean> sentToolCalls, ConcurrentHashMap<String, Boolean> sentToolResults, int[] llmCallCount) throws Exception {
        Msg msg = event.getMessage();
        if (msg == null || msg.getContent() == null) {
            return;
        }

        log.debug("[stream] Event received: isLast={}, type={}, contentBlocks={}", event.isLast(), event.getType(), msg.getContent().size());

        // Process ALL events (including isLast) for tool-related blocks
        // because ToolResultBlock may only appear in isLast events
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof ThinkingBlock tb) {
                String thinking = tb.getThinking();
                if (thinking != null && !thinking.isEmpty()) {
                    sendEvent(emitter, "thinking", Map.of("content", thinking));
                }
            } else if (block instanceof TextBlock tb) {
                // Skip TextBlock in isLast events as they are duplicates
                if (!event.isLast()) {
                    String text = tb.getText();
                    if (text != null && !text.isEmpty()) {
                        sendEvent(emitter, "text", Map.of("content", text));
                    }
                }
            } else if (block instanceof ToolUseBlock tub) {
                String toolId = tub.getId();
                // Deduplicate: only send tool_call event once per toolId
                if (toolId != null && sentToolCalls.putIfAbsent(toolId, Boolean.TRUE) == null) {
                    toolCallStartTimes.put(toolId, System.currentTimeMillis());
                    sendEvent(emitter, "tool_call", Map.of(
                            "id", toolId,
                            "name", tub.getName() != null ? tub.getName() : "",
                            "params", tub.getInput() != null ? tub.getInput().toString() : "{}",
                            "timestamp", System.currentTimeMillis()
                    ));
                    log.debug("[tool_call] New tool call: id={}, name={}", toolId, tub.getName());
                } else {
                    log.debug("[tool_call] Duplicate tool call skipped: id={}", toolId);
                }
            } else if (block instanceof ToolResultBlock trb) {
                String resultText = trb.getOutput() != null
                        ? trb.getOutput().stream()
                        .filter(o -> o instanceof TextBlock)
                        .map(o -> ((TextBlock) o).getText())
                        .reduce("", (a, b) -> a + b)
                        : "";
                String toolId = trb.getId();
                // Deduplicate: only send tool_result event once per toolId
                if (toolId != null && sentToolResults.putIfAbsent(toolId, Boolean.TRUE) == null) {
                    Long startTime = toolCallStartTimes.remove(toolId);
                    long durationMs = startTime != null ? System.currentTimeMillis() - startTime : -1;

                    String resultPreview = resultText.length() > 200
                            ? resultText.substring(0, 200) + "..."
                            : resultText;

                    log.info("[tool_result] name={}, id={}, duration={}ms, resultLength={}, hasStartTime={}",
                            trb.getName(), toolId, durationMs, resultText.length(), startTime != null);

                    sendEvent(emitter, "tool_result", Map.of(
                            "id", toolId,
                            "name", trb.getName() != null ? trb.getName() : "",
                            "result", resultText,
                            "result_preview", resultPreview,
                            "duration_ms", durationMs,
                            "timestamp", System.currentTimeMillis()
                    ));
                } else {
                    log.debug("[tool_result] Duplicate tool result skipped: id={}", toolId);
                }
            }
        }

        if (event.isLast()) {
            log.info("[stream] isLast event received: type={}, contentBlocks={}", event.getType(), msg.getContent().size());
            ChatUsage usage = msg.getChatUsage();
            log.info("[stream] ChatUsage: {}", usage != null ? usage : "null");
            if (usage != null) {
                log.info("[stream] Usage detail: in={}, out={}, total={}, time={}",
                        usage.getInputTokens(), usage.getOutputTokens(),
                        usage.getTotalTokens(), usage.getTime());
            }
            if (usage != null) {
                llmCallCount[0]++;
                Double timeValue = usage.getTime();
                log.info("[usage] LLM call #{}: tokens={}/{} time={}s",
                        llmCallCount[0], usage.getInputTokens(), usage.getOutputTokens(), timeValue);
                sendEvent(emitter, "usage", Map.of(
                        "inputTokens", usage.getInputTokens(),
                        "outputTokens", usage.getOutputTokens(),
                        "totalTokens", usage.getTotalTokens(),
                        "time", timeValue != null ? timeValue : 0,
                        "callNumber", llmCallCount[0]
                ));
            } else {
                log.warn("[stream] ChatUsage is null in isLast event - model may not support usage stats");
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

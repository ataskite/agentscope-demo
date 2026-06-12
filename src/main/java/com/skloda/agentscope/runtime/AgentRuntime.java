package com.skloda.agentscope.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skloda.agentscope.hook.ApprovalHook;
import com.skloda.agentscope.hook.ObservabilityHook;
import com.skloda.agentscope.service.ApprovalService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.*;
import io.agentscope.core.message.*;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime container for a single Agent interaction session.
 * Uses agent.streamEvents() for automatic lifecycle events (AgentScope 2.0)
 * and EventSink for manual multi-agent events.
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
    }

    @Override
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        return stream(userMsg, false);
    }

    /**
     * Stream agent response as Flux.
     * Merges manual multi-agent events (from EventSink) with automatic agent lifecycle events.
     *
     * @param userMsg          the user message
     * @param isApprovalResume true if this is a resume after HITL approval
     */
    public Flux<Map<String, Object>> stream(Msg userMsg, boolean isApprovalResume) {
        log.debug("Starting stream for agent: {} (resume={})", agent.getName(), isApprovalResume);

        // Manual multi-agent events from EventSink (pipeline, routing, handoff, etc.)
        Flux<Map<String, Object>> sinkEvents = hook.getEventSink().asFlux();

        Flux<Map<String, Object>> agentEvents;
        if (isApprovalResume) {
            agentEvents = createApprovalResumeStream(userMsg);
        } else {
            agentEvents = agent.streamEvents(userMsg)
                    .map(this::mapAgentEvent)
                    .filter(map -> map != null && !map.isEmpty());
        }

        return Flux.merge(sinkEvents, agentEvents)
                .concatWith(Mono.fromCallable(() -> {
                    // On completion, check if approval was triggered
                    if (approvalHook != null && approvalHook.isApprovalTriggered()) {
                        return handleApprovalCompletion();
                    }
                    return Map.of("type", "done");
                }))
                .doOnCancel(this::close)
                .doOnComplete(() -> {
                    hook.getEventSink().complete();
                    this.close();
                })
                .doOnError(e -> {
                    log.error("Stream error for agent: {}", agent.getName(), e);
                    hook.getEventSink().complete();
                    this.close();
                });
    }

    /**
     * Approval resume path: uses agent.call() to continue executing pending tools.
     * Emits text events from the call result.
     */
    private Flux<Map<String, Object>> createApprovalResumeStream(Msg userMsg) {
        return Flux.create(fluxSink -> {
            Mono<Msg> resumeCall = userMsg != null
                    ? agent.call(userMsg)
                    : agent.call();
            resumeCall
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
                            fluxSink::complete
                    );
        });
    }

    /**
     * Build the approval completion event and register the pending approval.
     */
    private Map<String, Object> handleApprovalCompletion() {
        String approvalId = approvalService.registerPendingApproval(
                agent, hook, approvalHook.getPendingToolUseBlocks(), agentId, sessionId);
        return Map.of(
                "type", "pending_approval",
                "approvalId", approvalId,
                "agentId", agentId != null ? agentId : "",
                "toolCalls", approvalHook.getPendingToolCallsForSse(),
                "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * Convert an AgentEvent from streamEvents() into an SSE-compatible Map.
     * Returns null for events that should be silently consumed.
     */
    private Map<String, Object> mapAgentEvent(AgentEvent event) {
        try {
            AgentEventType type = event.getType();
            return switch (type) {
                case TEXT_BLOCK_DELTA -> {
                    if (event instanceof TextBlockDeltaEvent tde) {
                        String delta = tde.getDelta();
                        if (delta != null && !delta.isEmpty()) {
                            yield Map.of("type", "text", "content", delta);
                        }
                    }
                    yield null;
                }
                case THINKING_BLOCK_DELTA -> {
                    if (event instanceof ThinkingBlockDeltaEvent tde) {
                        String delta = tde.getDelta();
                        if (delta != null && !delta.isEmpty()) {
                            yield Map.of("type", "thinking", "content", delta);
                        }
                    }
                    yield null;
                }
                case AGENT_START -> {
                    if (event instanceof AgentStartEvent ase) {
                        yield Map.<String, Object>of(
                                "type", "agent_start",
                                "name", ase.getName() != null ? ase.getName() : "",
                                "role", ase.getRole() != null ? ase.getRole() : "",
                                "timestamp", System.currentTimeMillis()
                        );
                    }
                    yield Map.of("type", "agent_start", "timestamp", System.currentTimeMillis());
                }
                case AGENT_END -> Map.of("type", "agent_end", "timestamp", System.currentTimeMillis());
                case AGENT_RESULT -> {
                    if (event instanceof AgentResultEvent are) {
                        Msg result = are.getResult();
                        if (result != null && result.getContent() != null) {
                            StringBuilder sb = new StringBuilder();
                            for (ContentBlock block : result.getContent()) {
                                if (block instanceof TextBlock tb && tb.getText() != null) {
                                    sb.append(tb.getText());
                                }
                            }
                            if (!sb.isEmpty()) {
                                yield Map.of("type", "agent_result_text", "content", sb.toString(),
                                        "timestamp", System.currentTimeMillis());
                            }
                        }
                    }
                    yield null;
                }
                case MODEL_CALL_START -> Map.of("type", "llm_start", "timestamp", System.currentTimeMillis());
                case MODEL_CALL_END -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("type", "llm_end");
                    if (event instanceof ModelCallEndEvent mcee && mcee.getUsage() != null) {
                        data.put("inputTokens", mcee.getUsage().getInputTokens());
                        data.put("outputTokens", mcee.getUsage().getOutputTokens());
                        data.put("totalTokens", mcee.getUsage().getTotalTokens());
                    }
                    data.put("timestamp", System.currentTimeMillis());
                    yield data;
                }
                case TOOL_CALL_START -> {
                    if (event instanceof ToolCallStartEvent tcse) {
                        yield Map.<String, Object>of(
                                "type", "tool_start",
                                "toolName", tcse.getToolCallName() != null ? tcse.getToolCallName() : "",
                                "toolCallId", tcse.getToolCallId() != null ? tcse.getToolCallId() : "",
                                "timestamp", System.currentTimeMillis()
                        );
                    }
                    yield Map.of("type", "tool_start", "timestamp", System.currentTimeMillis());
                }
                case TOOL_CALL_END -> {
                    if (event instanceof ToolCallEndEvent tcee) {
                        yield Map.<String, Object>of(
                                "type", "tool_end",
                                "toolName", tcee.getToolCallName() != null ? tcee.getToolCallName() : "",
                                "toolCallId", tcee.getToolCallId() != null ? tcee.getToolCallId() : "",
                                "timestamp", System.currentTimeMillis()
                        );
                    }
                    yield Map.of("type", "tool_end", "timestamp", System.currentTimeMillis());
                }
                case TOOL_RESULT_START -> {
                    if (event instanceof ToolResultStartEvent trse) {
                        yield Map.<String, Object>of(
                                "type", "tool_result_start",
                                "toolName", trse.getToolCallName() != null ? trse.getToolCallName() : "",
                                "toolCallId", trse.getToolCallId() != null ? trse.getToolCallId() : "",
                                "timestamp", System.currentTimeMillis()
                        );
                    }
                    yield null;
                }
                case TOOL_RESULT_END -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("type", "tool_result_end");
                    if (event instanceof ToolResultEndEvent tree) {
                        if (tree.getToolCallName() != null) {
                            data.put("toolName", tree.getToolCallName());
                        }
                        if (tree.getState() != null) {
                            data.put("state", tree.getState().name());
                        }
                    }
                    data.put("timestamp", System.currentTimeMillis());
                    yield data;
                }
                case TOOL_RESULT_TEXT_DELTA -> {
                    if (event instanceof ToolResultTextDeltaEvent trtde) {
                        String delta = trtde.getDelta();
                        if (delta != null && !delta.isEmpty()) {
                            yield Map.of("type", "tool_result_delta", "content", delta,
                                    "toolName", trtde.getToolCallName() != null ? trtde.getToolCallName() : "",
                                    "timestamp", System.currentTimeMillis());
                        }
                    }
                    yield null;
                }
                case EXCEED_MAX_ITERS -> {
                    if (event instanceof ExceedMaxItersEvent emie) {
                        yield Map.<String, Object>of(
                                "type", "exceed_max_iters",
                                "maxIters", emie.getMaxIters(),
                                "currentIter", emie.getCurrentIter(),
                                "timestamp", System.currentTimeMillis()
                        );
                    }
                    yield Map.of("type", "exceed_max_iters", "timestamp", System.currentTimeMillis());
                }
                case REQUIRE_USER_CONFIRM -> {
                    if (event instanceof RequireUserConfirmEvent ruce) {
                        yield Map.<String, Object>of(
                                "type", "require_user_confirm",
                                "toolCalls", ruce.getToolCalls(),
                                "timestamp", System.currentTimeMillis()
                        );
                    }
                    yield Map.of("type", "require_user_confirm", "timestamp", System.currentTimeMillis());
                }
                case REQUEST_STOP -> Map.of("type", "request_stop", "timestamp", System.currentTimeMillis());
                case HINT_BLOCK -> {
                    if (event instanceof HintBlockEvent hbe) {
                        yield Map.<String, Object>of(
                                "type", "hint",
                                "hint", hbe.getHint() != null ? hbe.getHint() : "",
                                "source", hbe.getHintSource() != null ? hbe.getHintSource() : "",
                                "timestamp", System.currentTimeMillis()
                        );
                    }
                    yield null;
                }
                case CUSTOM -> {
                    if (event instanceof CustomEvent ce) {
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("type", ce.getName() != null ? ce.getName() : "custom");
                        if (ce.getValue() != null) {
                            data.putAll(ce.getValue());
                        }
                        data.put("timestamp", System.currentTimeMillis());
                        yield data;
                    }
                    yield null;
                }
                // Block boundary events: useful for streaming coordination but no SSE content
                case TEXT_BLOCK_START, TEXT_BLOCK_END,
                     THINKING_BLOCK_START, THINKING_BLOCK_END,
                     DATA_BLOCK_START, DATA_BLOCK_DELTA, DATA_BLOCK_END,
                     TOOL_CALL_DELTA,
                     TOOL_RESULT_DATA_DELTA,
                     SUBAGENT_EXPOSED,
                     USER_CONFIRM_RESULT,
                     EXTERNAL_EXECUTION_RESULT,
                     REQUIRE_EXTERNAL_EXECUTION -> null;
            };
        } catch (Exception e) {
            log.error("Error mapping agent event: {}", event, e);
            return Map.of("type", "error", "message", "Event mapping error: " + e.getMessage());
        }
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

    @Override
    public void close() {
        hook.reset();
        if (onClose != null) {
            onClose.run();
        }
        log.debug("AgentRuntime closed for agent: {}", agent.getName());
    }
}

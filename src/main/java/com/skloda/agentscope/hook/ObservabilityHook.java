package com.skloda.agentscope.hook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.hook.*;
import io.agentscope.core.message.*;
import io.agentscope.core.model.ChatUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * AgentScope Hook that captures the full agent lifecycle and emits SSE events
 * for real-time observability timeline in the debug panel.
 *
 * Timeline events emitted:
 *   agent_start  → PreCallEvent (agent begins processing)
 *   llm_start    → PreReasoningEvent (LLM call begins)
 *   thinking     → ReasoningChunkEvent (streaming thinking text)
 *   llm_end      → PostReasoningEvent (LLM call ends, with usage)
 *   tool_start   → PreActingEvent (tool execution begins)
 *   tool_end     → PostActingEvent (tool execution ends)
 *   agent_end    → PostCallEvent (agent finishes)
 *   error        → ErrorEvent
 */
public class ObservabilityHook implements Hook {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityHook.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Multi-agent events
    public static final String PIPELINE_START = "pipeline_start";
    public static final String PIPELINE_STEP_START = "pipeline_step_start";
    public static final String PIPELINE_STEP_END = "pipeline_step_end";
    public static final String PIPELINE_END = "pipeline_end";
    public static final String ROUTING_START = "routing_start";
    public static final String ROUTING_DECISION = "routing_decision";
    public static final String ROUTING_END = "routing_end";
    public static final String HANDOFF_START = "handoff_start";
    public static final String HANDOFF_COMPLETE = "handoff_complete";
    public static final String HANDOFF_ERROR = "handoff_error";

    /** Per-session event consumer: (eventType, dataMap) */
    private final List<BiConsumer<String, Map<String, Object>>> consumers = Collections.synchronizedList(new ArrayList<>());

    private final ConcurrentHashMap<String, Long> toolStartNanos = new ConcurrentHashMap<>();
    private final AtomicInteger llmCallCount = new AtomicInteger(0);
    private final AtomicInteger toolCallCount = new AtomicInteger(0);
    private long agentStartNanos;

    public void addConsumer(BiConsumer<String, Map<String, Object>> consumer) {
        consumers.add(consumer);
    }

    public void removeConsumer(BiConsumer<String, Map<String, Object>> consumer) {
        consumers.remove(consumer);
    }

    public void reset() {
        toolStartNanos.clear();
        llmCallCount.set(0);
        toolCallCount.set(0);
        agentStartNanos = 0;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        try {
            if (event instanceof PreCallEvent e) {
                handlePreCall(e);
            } else if (event instanceof PreReasoningEvent e) {
                handlePreReasoning(e);
            } else if (event instanceof ReasoningChunkEvent e) {
                handleReasoningChunk(e);
            } else if (event instanceof PostReasoningEvent e) {
                handlePostReasoning(e);
            } else if (event instanceof PreActingEvent e) {
                handlePreActing(e);
            } else if (event instanceof PostActingEvent e) {
                handlePostActing(e);
            } else if (event instanceof PostCallEvent e) {
                handlePostCall(e);
            } else if (event instanceof ErrorEvent e) {
                handleError(e);
            }
        } catch (Exception ex) {
            log.error("Error in ObservabilityHook.onEvent", ex);
        }
        return Mono.just(event);
    }

    // ---- PreCall: agent starts processing ----
    private void handlePreCall(PreCallEvent e) {
        agentStartNanos = System.nanoTime();
        int msgCount = e.getInputMessages() != null ? e.getInputMessages().size() : 0;
        emit("agent_start", Map.of(
                "agentName", e.getAgent().getName() != null ? e.getAgent().getName() : "",
                "inputMessageCount", msgCount,
                "timestamp", e.getTimestamp()
        ));
    }

    // ---- PreReasoning: LLM call begins ----
    private void handlePreReasoning(PreReasoningEvent e) {
        int callNum = llmCallCount.incrementAndGet();
        int msgCount = e.getInputMessages() != null ? e.getInputMessages().size() : 0;
        emit("llm_start", Map.of(
                "callNumber", callNum,
                "modelName", e.getModelName() != null ? e.getModelName() : "",
                "inputMessageCount", msgCount,
                "timestamp", System.currentTimeMillis()
        ));
    }

    // ---- ReasoningChunk: streaming thinking text ----
    private void handleReasoningChunk(ReasoningChunkEvent e) {
        Msg chunk = e.getIncrementalChunk();
        if (chunk == null || chunk.getContent() == null) return;

        for (ContentBlock block : chunk.getContent()) {
            if (block instanceof ThinkingBlock tb) {
                String thinking = tb.getThinking();
                if (thinking != null && !thinking.isEmpty()) {
                    emit("thinking", Map.of("content", thinking));
                }
            } else if (block instanceof TextBlock tb) {
                String text = tb.getText();
                if (text != null && !text.isEmpty()) {
                    emit("reasoning_text", Map.of("content", text));
                }
            }
        }
    }

    // ---- PostReasoning: LLM call ends ----
    private void handlePostReasoning(PostReasoningEvent e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("callNumber", llmCallCount.get());
        data.put("timestamp", System.currentTimeMillis());

        Msg reasoningMsg = e.getReasoningMessage();
        if (reasoningMsg != null) {
            ChatUsage usage = reasoningMsg.getChatUsage();
            if (usage != null) {
                data.put("inputTokens", usage.getInputTokens());
                data.put("outputTokens", usage.getOutputTokens());
                data.put("totalTokens", usage.getTotalTokens());
                data.put("llmTime", usage.getTime());
            }
            // Extract tool calls from reasoning result
            List<Map<String, Object>> toolCalls = new ArrayList<>();
            for (ContentBlock block : reasoningMsg.getContent()) {
                if (block instanceof ToolUseBlock tub) {
                    toolCalls.add(Map.of(
                            "id", tub.getId() != null ? tub.getId() : "",
                            "name", tub.getName() != null ? tub.getName() : "",
                            "input", tub.getInput() != null ? tub.getInput().toString() : "{}"
                    ));
                }
            }
            if (!toolCalls.isEmpty()) {
                data.put("toolCalls", toolCalls);
            }
        }

        emit("llm_end", data);
    }

    // ---- PreActing: tool execution begins ----
    private void handlePreActing(PreActingEvent e) {
        int toolNum = toolCallCount.incrementAndGet();
        ToolUseBlock toolUse = e.getToolUse();
        String toolId = toolUse.getId() != null ? toolUse.getId() : "tool-" + toolNum;
        toolStartNanos.put(toolId, System.nanoTime());

        String paramsStr = toolUse.getInput() != null ? toolUse.getInput().toString() : "{}";
        String paramsPreview = paramsStr.length() > 80 ? paramsStr.substring(0, 80) + "..." : paramsStr;
        String toolName = toolUse.getName() != null ? toolUse.getName() : "";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", toolId);
        data.put("name", toolName);
        data.put("params", paramsStr);
        data.put("paramsPreview", paramsPreview);
        data.put("callNumber", toolNum);
        data.put("timestamp", System.currentTimeMillis());

        // Identify skill-loading calls
        if ("load_skill_through_path".equals(toolName)) {
            data.put("isSkill", true);
            data.put("displayName", extractSkillName(paramsStr));
        }

        log.info("[tool_start] {} #{} paramsPreview: {}", toolName, toolNum, paramsPreview);
        emit("tool_start", data);
    }

    // ---- PostActing: tool execution ends ----
    private void handlePostActing(PostActingEvent e) {
        ToolUseBlock toolUse = e.getToolUse();
        ToolResultBlock result = e.getToolResult();
        String toolId = toolUse.getId() != null ? toolUse.getId() : "tool-unknown";

        Long startNanos = toolStartNanos.remove(toolId);
        long durationMs = startNanos != null ? (System.nanoTime() - startNanos) / 1_000_000 : -1;

        String resultText = "";
        if (result != null && result.getOutput() != null) {
            resultText = result.getOutput().stream()
                    .filter(o -> o instanceof TextBlock)
                    .map(o -> ((TextBlock) o).getText())
                    .reduce("", (a, b) -> a + b);
        }

        String resultPreview = resultText.length() > 200
                ? resultText.substring(0, 200) + "..."
                : resultText;

        boolean isSuccess = resultText.isEmpty() || !resultText.startsWith("Error");
        String toolName = toolUse.getName() != null ? toolUse.getName() : "";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", toolId);
        data.put("name", toolName);
        data.put("result", resultText);
        data.put("resultPreview", resultPreview);
        data.put("duration_ms", durationMs);
        data.put("success", isSuccess);
        data.put("timestamp", System.currentTimeMillis());

        if ("load_skill_through_path".equals(toolName)) {
            data.put("isSkill", true);
        }

        log.info("[tool_end] {} durationMs={} success={}", toolName, durationMs, isSuccess);
        emit("tool_end", data);
    }

    // ---- PostCall: agent finishes ----
    private void handlePostCall(PostCallEvent e) {
        long totalMs = agentStartNanos > 0 ? (System.nanoTime() - agentStartNanos) / 1_000_000 : 0;
        emit("agent_end", Map.of(
                "totalLlmCalls", llmCallCount.get(),
                "totalToolCalls", toolCallCount.get(),
                "duration_ms", totalMs,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /** Extract skill name from load_skill_through_path params.
     *  e.g. "{skillId=docx_classpath-skills, path=SKILL.md}" → "docx"
     *  e.g. "{skillId=bank_invoice_java_classpath-skills, path=SKILL.md}" → "bank_invoice_java"
     */
    private String extractSkillName(String paramsStr) {
        if (paramsStr == null) return null;
        // Match skillId=<name>_classpath-skills
        int idx = paramsStr.indexOf("skillId=");
        if (idx >= 0) {
            String sub = paramsStr.substring(idx + 8); // skip "skillId="
            // Look for the _classpath-skills suffix
            int end = sub.indexOf("_classpath-skills");
            if (end > 0) {
                return sub.substring(0, end);
            }
            // Fallback: look for any underscore as delimiter
            end = sub.indexOf('_');
            if (end > 0) {
                return sub.substring(0, end);
            }
            // No suffix, take until comma/space/brace
            end = sub.indexOf(',');
            if (end > 0) return sub.substring(0, end);
        }
        return null;
    }

    // ---- Error ----
    private void handleError(ErrorEvent e) {
        emit("error", Map.of(
                "message", e.getError() != null ? e.getError().getMessage() : "Unknown error",
                "timestamp", System.currentTimeMillis()
        ));
    }

    // ---- Multi-agent event emitters ----

    public void emitPipelineStart(String pipelineId, List<String> subAgents) {
        emit(PIPELINE_START, Map.of(
                "pipelineId", pipelineId,
                "subAgents", subAgents,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitPipelineStepStart(String pipelineId, int stepIndex, String agentId) {
        emit(PIPELINE_STEP_START, Map.of(
                "pipelineId", pipelineId,
                "stepIndex", stepIndex,
                "agentId", agentId,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitPipelineStepEnd(String pipelineId, int stepIndex, String agentId, long durationMs) {
        emit(PIPELINE_STEP_END, Map.of(
                "pipelineId", pipelineId,
                "stepIndex", stepIndex,
                "agentId", agentId,
                "duration_ms", durationMs,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitPipelineEnd(String pipelineId, int totalSteps, long totalDurationMs) {
        emit(PIPELINE_END, Map.of(
                "pipelineId", pipelineId,
                "totalSteps", totalSteps,
                "duration_ms", totalDurationMs,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitRoutingDecision(String routingId, String selectedAgent, String reasoning) {
        emit(ROUTING_DECISION, Map.of(
                "routingId", routingId,
                "selectedAgent", selectedAgent,
                "reasoning", reasoning,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitRoutingEnd(String routingId, String selectedAgent) {
        emit(ROUTING_END, Map.of(
                "routingId", routingId,
                "selectedAgent", selectedAgent,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitHandoffStart(String fromAgent, String toAgent, String reason) {
        emit(HANDOFF_START, Map.of(
                "fromAgent", fromAgent,
                "toAgent", toAgent,
                "reason", reason,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitHandoffComplete(String fromAgent, String toAgent) {
        emit(HANDOFF_COMPLETE, Map.of(
                "fromAgent", fromAgent,
                "toAgent", toAgent,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitHandoffError(String fromAgent, String toAgent, String error) {
        emit(HANDOFF_ERROR, Map.of(
                "fromAgent", fromAgent,
                "toAgent", toAgent,
                "error", error,
                "timestamp", System.currentTimeMillis()
        ));
    }

    // ---- Emit event to all consumers ----
    private void emit(String type, Map<String, Object> data) {
        log.debug("[hook] {}: {}", type, data);
        for (BiConsumer<String, Map<String, Object>> consumer : consumers) {
            try {
                consumer.accept(type, data);
            } catch (Exception e) {
                log.warn("Consumer error for event {}: {}", type, e.getMessage());
            }
        }
    }
}

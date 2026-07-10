package com.skloda.agentscope.runtime;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.AllToolsDeniedEvent;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.event.DataBlockDeltaEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.ExternalExecutionResultEvent;
import io.agentscope.core.event.HintBlockEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.ModelCallStartEvent;
import io.agentscope.core.event.RequireExternalExecutionEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.SubagentExposedEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultDataDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.event.UserConfirmResultEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-function mapping from an AgentScope 2.0 {@link AgentEvent} to an SSE-compatible
 * {@code Map<String,Object>} payload consumed by the frontend (a JSON object whose {@code type}
 * field selects the UI handler).
 *
 * <p>Covers all 30 {@link AgentEventType} values. Returns {@code null} for events the frontend
 * does not need to render (block boundaries). This class is extracted from {@code AgentRuntime}
 * so it can be unit-tested in isolation and reused by the pipeline runtimes.
 *
 * <p>HITL note: the demo's active approval flow goes through {@code ApprovalMiddleware}
 * (suppresses tool execution and emits a {@code pending_approval} SSE from {@code AgentRuntime}).
 * {@link AgentEventType#REQUIRE_USER_CONFIRM} / {@link AgentEventType#USER_CONFIRM_RESULT} are
 * <em>passthrough placeholders</em> for the native RC3 confirm flow — the framework's permission
 * system publishes {@code RequireUserConfirmEvent} when a tool matches an {@code ASK} rule, which
 * is reserved as a future migration path (see ROADMAP P1). They are mapped (not dropped) so the
 * frontend can render them if/when activated, and so the contract is locked by unit tests.
 */
public class AgentEventMapper {

    private static final Logger log = LoggerFactory.getLogger(AgentEventMapper.class);

    /**
     * Map one event. Returns {@code null} to signal "drop this event".
     * On mapping error, returns an {@code error} payload (never throws).
     */
    public Map<String, Object> apply(AgentEvent event) {
        if (event == null) {
            return null;
        }
        try {
            AgentEventType type = event.getType();
            return switch (type) {
                case TEXT_BLOCK_DELTA -> textDelta(event);
                case THINKING_BLOCK_DELTA -> thinkingDelta(event);
                case AGENT_START -> agentStart(event);
                case AGENT_END -> base("agent_end");
                case AGENT_RESULT -> agentResult(event);
                case MODEL_CALL_START -> modelCallStart(event);
                case MODEL_CALL_END -> modelCallEnd(event);
                case TOOL_CALL_START -> toolStart(event);
                case TOOL_CALL_DELTA -> toolCallDelta(event);
                case TOOL_CALL_END -> toolEnd(event);
                case TOOL_RESULT_START -> toolResultStart(event);
                case TOOL_RESULT_TEXT_DELTA -> toolResultTextDelta(event);
                case TOOL_RESULT_DATA_DELTA -> toolResultDataDelta(event);
                case TOOL_RESULT_END -> toolResultEnd(event);
                case DATA_BLOCK_DELTA -> dataBlockDelta(event);
                case EXCEED_MAX_ITERS -> exceedMaxIters(event);
                case REQUIRE_USER_CONFIRM -> requireUserConfirm(event);
                case REQUIRE_EXTERNAL_EXECUTION -> requireExternalExecution(event);
                case USER_CONFIRM_RESULT -> userConfirmResult(event);
                case EXTERNAL_EXECUTION_RESULT -> externalExecutionResult(event);
                case REQUEST_STOP -> base("request_stop");
                case HINT_BLOCK -> hintBlock(event);
                case SUBAGENT_EXPOSED -> subagentExposed(event);
                case ALL_TOOLS_DENIED -> allToolsDenied(event);
                case CUSTOM -> custom(event);
                // Block boundaries: useful for streaming coordination, but no SSE content for the UI.
                case TEXT_BLOCK_START, TEXT_BLOCK_END,
                        THINKING_BLOCK_START, THINKING_BLOCK_END,
                        DATA_BLOCK_START, DATA_BLOCK_END -> null;
            };
        } catch (Exception e) {
            log.error("Error mapping agent event: {}", event, e);
            return Map.of("type", "error", "message", "Event mapping error: " + e.getMessage());
        }
    }

    // ---- text / thinking ----

    private Map<String, Object> textDelta(AgentEvent event) {
        if (event instanceof TextBlockDeltaEvent tde) {
            String delta = tde.getDelta();
            if (delta != null && !delta.isEmpty()) {
                return Map.of("type", "text", "content", delta);
            }
        }
        return null;
    }

    private Map<String, Object> thinkingDelta(AgentEvent event) {
        if (event instanceof ThinkingBlockDeltaEvent tde) {
            String delta = tde.getDelta();
            if (delta != null && !delta.isEmpty()) {
                return Map.of("type", "thinking", "content", delta);
            }
        }
        return null;
    }

    // ---- agent lifecycle ----

    private Map<String, Object> agentStart(AgentEvent event) {
        if (event instanceof AgentStartEvent ase) {
            Map<String, Object> data = base("agent_start");
            data.put("name", ase.getName() != null ? ase.getName() : "");
            data.put("role", ase.getRole() != null ? ase.getRole() : "");
            return data;
        }
        return base("agent_start");
    }

    private Map<String, Object> agentResult(AgentEvent event) {
        if (event instanceof AgentResultEvent are) {
            Msg result = are.getResult();
            if (result != null && result.getContent() != null) {
                String text = extractText(result.getContent());
                if (!text.isEmpty()) {
                    Map<String, Object> data = base("agent_result_text");
                    data.put("content", text);
                    return data;
                }
            }
        }
        return null;
    }

    // ---- model calls ----

    private Map<String, Object> modelCallStart(AgentEvent event) {
        return base("llm_start");
    }

    private Map<String, Object> modelCallEnd(AgentEvent event) {
        Map<String, Object> data = base("llm_end");
        if (event instanceof ModelCallEndEvent mcee && mcee.getUsage() != null) {
            data.put("inputTokens", mcee.getUsage().getInputTokens());
            data.put("outputTokens", mcee.getUsage().getOutputTokens());
            data.put("totalTokens", mcee.getUsage().getTotalTokens());
        }
        return data;
    }

    // ---- tool calls ----

    private Map<String, Object> toolStart(AgentEvent event) {
        if (event instanceof ToolCallStartEvent tcse) {
            String toolName = tcse.getToolCallName() != null ? tcse.getToolCallName() : "";
            Map<String, Object> data = base("tool_start");
            data.put("toolName", toolName);
            // Frontend chat.js reads payload.name for timeline rows; keep both for compatibility
            data.put("name", toolName);
            data.put("toolCallId", tcse.getToolCallId() != null ? tcse.getToolCallId() : "");
            return data;
        }
        return base("tool_start");
    }

    private Map<String, Object> toolCallDelta(AgentEvent event) {
        if (event instanceof ToolCallDeltaEvent tcde) {
            String delta = tcde.getDelta();
            if (delta != null && !delta.isEmpty()) {
                Map<String, Object> data = base("tool_call_delta");
                data.put("toolName", tcde.getToolCallName() != null ? tcde.getToolCallName() : "");
                data.put("toolCallId", tcde.getToolCallId() != null ? tcde.getToolCallId() : "");
                data.put("content", delta);
                return data;
            }
        }
        return null;
    }

    private Map<String, Object> toolEnd(AgentEvent event) {
        if (event instanceof ToolCallEndEvent tcee) {
            String toolName = tcee.getToolCallName() != null ? tcee.getToolCallName() : "";
            Map<String, Object> data = base("tool_end");
            data.put("toolName", toolName);
            // Frontend chat.js reads payload.name for timeline rows; keep both for compatibility
            data.put("name", toolName);
            data.put("toolCallId", tcee.getToolCallId() != null ? tcee.getToolCallId() : "");
            return data;
        }
        return base("tool_end");
    }

    // ---- tool results ----

    private Map<String, Object> toolResultStart(AgentEvent event) {
        if (event instanceof ToolResultStartEvent trse) {
            String toolName = trse.getToolCallName() != null ? trse.getToolCallName() : "";
            Map<String, Object> data = base("tool_result_start");
            data.put("toolName", toolName);
            // Frontend chat.js reads payload.name for timeline rows; keep both for compatibility
            data.put("name", toolName);
            data.put("toolCallId", trse.getToolCallId() != null ? trse.getToolCallId() : "");
            return data;
        }
        return null;
    }

    private Map<String, Object> toolResultTextDelta(AgentEvent event) {
        if (event instanceof ToolResultTextDeltaEvent trtde) {
            String delta = trtde.getDelta();
            if (delta != null && !delta.isEmpty()) {
                Map<String, Object> data = base("tool_result_delta");
                data.put("content", delta);
                data.put("toolName", trtde.getToolCallName() != null ? trtde.getToolCallName() : "");
                return data;
            }
        }
        return null;
    }

    private Map<String, Object> toolResultDataDelta(AgentEvent event) {
        // Data deltas carry structured ContentBlock output; surface as a delta so the debug panel
        // can render streaming tool output uniformly with text deltas.
        if (event instanceof ToolResultDataDeltaEvent trdde) {
            ContentBlock data = trdde.getData();
            String preview = data != null ? String.valueOf(data) : "";
            if (!preview.isEmpty()) {
                Map<String, Object> map = base("tool_result_delta");
                map.put("content", preview);
                map.put("toolName", trdde.getToolCallName() != null ? trdde.getToolCallName() : "");
                return map;
            }
        }
        return null;
    }

    private Map<String, Object> toolResultEnd(AgentEvent event) {
        Map<String, Object> data = base("tool_result_end");
        if (event instanceof ToolResultEndEvent tree) {
            if (tree.getToolCallName() != null) {
                data.put("toolName", tree.getToolCallName());
                // Frontend chat.js reads payload.name for timeline rows; keep both for compatibility
                data.put("name", tree.getToolCallName());
            }
            if (tree.getState() != null) {
                data.put("state", tree.getState().name());
            }
        }
        return data;
    }

    // ---- data block ----

    private Map<String, Object> dataBlockDelta(AgentEvent event) {
        if (event instanceof DataBlockDeltaEvent dbde) {
            String delta = dbde.getDelta();
            if (delta != null && !delta.isEmpty()) {
                Map<String, Object> data = base("data_block_delta");
                data.put("blockId", dbde.getBlockId() != null ? dbde.getBlockId() : "");
                data.put("content", delta);
                return data;
            }
        }
        return null;
    }

    // ---- control / limits ----

    private Map<String, Object> exceedMaxIters(AgentEvent event) {
        Map<String, Object> data = base("exceed_max_iters");
        if (event instanceof ExceedMaxItersEvent emie) {
            data.put("maxIters", emie.getMaxIters());
            data.put("currentIter", emie.getCurrentIter());
        }
        return data;
    }

    // ---- HITL (placeholders for native RC3 confirm flow; see class Javadoc) ----

    private Map<String, Object> requireUserConfirm(AgentEvent event) {
        Map<String, Object> data = base("require_user_confirm");
        if (event instanceof RequireUserConfirmEvent ruce) {
            data.put("toolCalls", summarizeToolUseBlocks(ruce.getToolCalls()));
        }
        return data;
    }

    private Map<String, Object> requireExternalExecution(AgentEvent event) {
        Map<String, Object> data = base("require_external_execution");
        if (event instanceof RequireExternalExecutionEvent reee) {
            data.put("toolCalls", summarizeToolUseBlocks(reee.getToolCalls()));
        }
        return data;
    }

    private Map<String, Object> userConfirmResult(AgentEvent event) {
        Map<String, Object> data = base("user_confirm_result");
        if (event instanceof UserConfirmResultEvent ucre && ucre.getConfirmResults() != null) {
            List<Map<String, Object>> results = ucre.getConfirmResults().stream()
                    .map(cr -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("confirmed", cr.isConfirmed());
                        if (cr.getToolCall() != null) {
                            r.put("toolName", cr.getToolCall().getName());
                        }
                        return r;
                    })
                    .toList();
            data.put("results", results);
        }
        return data;
    }

    private Map<String, Object> externalExecutionResult(AgentEvent event) {
        Map<String, Object> data = base("external_execution_result");
        if (event instanceof ExternalExecutionResultEvent eere && eere.getToolResults() != null) {
            data.put("count", eere.getToolResults().size());
        }
        return data;
    }

    // ---- hints / custom / subagent ----

    private Map<String, Object> hintBlock(AgentEvent event) {
        if (event instanceof HintBlockEvent hbe) {
            Map<String, Object> data = base("hint");
            data.put("hint", hbe.getHint() != null ? hbe.getHint() : "");
            data.put("source", hbe.getHintSource() != null ? hbe.getHintSource() : "");
            return data;
        }
        return null;
    }

    private Map<String, Object> subagentExposed(AgentEvent event) {
        Map<String, Object> data = base("subagent_exposed");
        if (event instanceof SubagentExposedEvent see) {
            data.put("subagentId", see.getSubagentId() != null ? see.getSubagentId() : "");
            data.put("agentId", see.getAgentId() != null ? see.getAgentId() : "");
            data.put("label", see.getLabel() != null ? see.getLabel() : "");
        }
        return data;
    }

    private Map<String, Object> allToolsDenied(AgentEvent event) {
        Map<String, Object> data = base("all_tools_denied");
        if (event instanceof AllToolsDeniedEvent atde) {
            data.put("toolCalls", summarizeToolUseBlocks(atde.getDeniedToolCalls()));
        }
        return data;
    }

    private Map<String, Object> custom(AgentEvent event) {
        if (event instanceof CustomEvent ce) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("type", ce.getName() != null ? ce.getName() : "custom");
            if (ce.getValue() != null) {
                data.putAll(ce.getValue());
            }
            data.put("timestamp", System.currentTimeMillis());
            return data;
        }
        return null;
    }

    // ---- helpers ----

    /** Base payload: type + timestamp, mutable (LinkedHashMap so insertion order is stable). */
    private Map<String, Object> base(String type) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("timestamp", System.currentTimeMillis());
        return data;
    }

    private static String extractText(List<ContentBlock> blocks) {
        if (blocks == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block instanceof TextBlock tb && tb.getText() != null) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    /** Compact, JSON-safe summary of ToolUseBlocks for the frontend (avoids serializing raw inputs). */
    private static List<Map<String, Object>> summarizeToolUseBlocks(List<ToolUseBlock> blocks) {
        if (blocks == null) {
            return List.of();
        }
        return blocks.stream()
                .map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", b.getName());
                    return m;
                })
                .toList();
    }
}

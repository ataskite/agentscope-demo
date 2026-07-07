package com.skloda.agentscope.runtime;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.ConfirmResult;
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
import io.agentscope.core.event.TextBlockEndEvent;
import io.agentscope.core.event.TextBlockStartEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultDataDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.event.UserConfirmResultEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the {@link AgentEventMapper} SSE contract for every {@link AgentEventType}.
 * Each test constructs the concrete event with a convenience ctor and asserts the
 * resulting {@code Map}'s {@code type} field plus the type-specific payload fields.
 *
 * <p>These tests double as a regression guard: if a future AgentScope rename changes an
 * event class shape, the corresponding test fails here rather than silently dropping
 * events at the SSE boundary.
 */
class AgentEventMapperTest {

    private final AgentEventMapper mapper = new AgentEventMapper();

    // ---- text / thinking deltas ----

    @Test
    void textBlockDelta_mapsToTextEvent() {
        AgentEvent event = new TextBlockDeltaEvent("reply-1", "block-1", "hello");
        Map<String, Object> out = mapper.apply(event);

        assertNotNull(out);
        assertEquals("text", out.get("type"));
        assertEquals("hello", out.get("content"));
    }

    @Test
    void textBlockDelta_emptyDelta_isDropped() {
        AgentEvent event = new TextBlockDeltaEvent("reply-1", "block-1", "");
        assertNull(mapper.apply(event));
    }

    @Test
    void thinkingBlockDelta_mapsToThinkingEvent() {
        AgentEvent event = new ThinkingBlockDeltaEvent("reply-1", "block-1", "reasoning");
        Map<String, Object> out = mapper.apply(event);

        assertEquals("thinking", out.get("type"));
        assertEquals("reasoning", out.get("content"));
    }

    // ---- block boundaries (dropped) ----

    @Test
    void textBlockStart_isDropped() {
        assertNull(mapper.apply(new TextBlockStartEvent("reply-1", "block-1")));
    }

    @Test
    void textBlockEnd_isDropped() {
        assertNull(mapper.apply(new TextBlockEndEvent("reply-1", "block-1")));
    }

    @Test
    void dataBlockDelta_mapsToDataBlockDelta() {
        AgentEvent event = new DataBlockDeltaEvent("reply-1", "block-1", "{\"k\":1}");
        Map<String, Object> out = mapper.apply(event);

        assertEquals("data_block_delta", out.get("type"));
        assertEquals("block-1", out.get("blockId"));
        assertEquals("{\"k\":1}", out.get("content"));
    }

    // ---- agent lifecycle ----

    @Test
    void agentStart_mapsNameAndRole() {
        AgentEvent event = new AgentStartEvent("session-1", "reply-1", "invoice-agent");
        Map<String, Object> out = mapper.apply(event);

        assertEquals("agent_start", out.get("type"));
        assertEquals("invoice-agent", out.get("name"));
        assertNotNull(out.get("role"));
    }

    @Test
    void agentResult_extractsTextFromMsg() {
        Msg result = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text("final answer").build())
                .build();
        AgentEvent event = new AgentResultEvent(result);
        Map<String, Object> out = mapper.apply(event);

        assertEquals("agent_result_text", out.get("type"));
        assertEquals("final answer", out.get("content"));
    }

    // ---- model calls ----

    @Test
    void modelCallStart_mapsToLlmStart() {
        AgentEvent event = new ModelCallStartEvent("reply-1");
        Map<String, Object> out = mapper.apply(event);

        assertEquals("llm_start", out.get("type"));
    }

    @Test
    void modelCallEnd_mapsTokensFromUsage() {
        AgentEvent event = new ModelCallEndEvent("reply-1", new ChatUsage(120, 80, 0.5));
        Map<String, Object> out = mapper.apply(event);

        assertEquals("llm_end", out.get("type"));
        assertEquals(120, out.get("inputTokens"));
        assertEquals(80, out.get("outputTokens"));
        assertEquals(200, out.get("totalTokens"));
    }

    // ---- tool calls ----

    @Test
    void toolCallStart_mapsNameAndId() {
        AgentEvent event = new ToolCallStartEvent("reply-1", "call-1", "parse_docx");
        Map<String, Object> out = mapper.apply(event);

        assertEquals("tool_start", out.get("type"));
        assertEquals("parse_docx", out.get("toolName"));
        assertEquals("parse_docx", out.get("name")); // frontend chat.js reads payload.name
        assertEquals("call-1", out.get("toolCallId"));
    }

    @Test
    void toolCallDelta_mapsContentAndName() {
        AgentEvent event = new ToolCallDeltaEvent("reply-1", "call-1", "parse_docx", "{\"path\":");
        Map<String, Object> out = mapper.apply(event);

        assertEquals("tool_call_delta", out.get("type"));
        assertEquals("parse_docx", out.get("toolName"));
        assertEquals("{\"path\":", out.get("content"));
    }

    @Test
    void toolCallEnd_mapsNameAndId() {
        AgentEvent event = new ToolCallEndEvent("reply-1", "call-1", "parse_docx");
        Map<String, Object> out = mapper.apply(event);

        assertEquals("tool_end", out.get("type"));
        assertEquals("parse_docx", out.get("toolName"));
        assertEquals("parse_docx", out.get("name")); // frontend chat.js reads payload.name
    }

    // ---- tool results ----

    @Test
    void toolResultStart_mapsNameAndId() {
        AgentEvent event = new ToolResultStartEvent("reply-1", "call-1", "parse_docx");
        Map<String, Object> out = mapper.apply(event);

        assertEquals("tool_result_start", out.get("type"));
        assertEquals("parse_docx", out.get("toolName"));
        assertEquals("parse_docx", out.get("name")); // frontend chat.js reads payload.name
    }

    @Test
    void toolResultTextDelta_mapsContent() {
        AgentEvent event = new ToolResultTextDeltaEvent("reply-1", "call-1", "parse_docx", "page 1...");
        Map<String, Object> out = mapper.apply(event);

        assertEquals("tool_result_delta", out.get("type"));
        assertEquals("page 1...", out.get("content"));
        assertEquals("parse_docx", out.get("toolName"));
    }

    @Test
    void toolResultEnd_mapsState() {
        AgentEvent event = new ToolResultEndEvent("reply-1", "call-1", "parse_docx", ToolResultState.SUCCESS);
        Map<String, Object> out = mapper.apply(event);

        assertEquals("tool_result_end", out.get("type"));
        assertEquals("parse_docx", out.get("toolName"));
        assertEquals("parse_docx", out.get("name")); // frontend chat.js reads payload.name
        assertEquals("SUCCESS", out.get("state"));
    }

    // ---- control / limits ----

    @Test
    void exceedMaxIters_mapsCounts() {
        AgentEvent event = new ExceedMaxItersEvent("reply-1", 10, 11);
        Map<String, Object> out = mapper.apply(event);

        assertEquals("exceed_max_iters", out.get("type"));
        assertEquals(10, out.get("maxIters"));
        assertEquals(11, out.get("currentIter"));
    }

    // ---- HITL passthrough placeholders (see AgentEventMapper Javadoc) ----

    @Test
    void requireUserConfirm_summarizesToolCalls() {
        ToolUseBlock block = ToolUseBlock.builder().name("write_file").build();
        AgentEvent event = new RequireUserConfirmEvent("reply-1", List.of(block));
        Map<String, Object> out = mapper.apply(event);

        assertEquals("require_user_confirm", out.get("type"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) out.get("toolCalls");
        assertEquals(1, toolCalls.size());
        assertEquals("write_file", toolCalls.get(0).get("name"));
    }

    @Test
    void requireExternalExecution_summarizesToolCalls() {
        ToolUseBlock block = ToolUseBlock.builder().name("run_code").build();
        AgentEvent event = new RequireExternalExecutionEvent("reply-1", List.of(block));
        Map<String, Object> out = mapper.apply(event);

        assertEquals("require_external_execution", out.get("type"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) out.get("toolCalls");
        assertEquals("run_code", toolCalls.get(0).get("name"));
    }

    @Test
    void userConfirmResult_mapsConfirmedFlag() {
        ToolUseBlock block = ToolUseBlock.builder().name("write_file").build();
        AgentEvent event = new UserConfirmResultEvent("reply-1", List.of(new ConfirmResult(true, block)));
        Map<String, Object> out = mapper.apply(event);

        assertEquals("user_confirm_result", out.get("type"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) out.get("results");
        assertEquals(1, results.size());
        assertEquals(true, results.get(0).get("confirmed"));
        assertEquals("write_file", results.get(0).get("toolName"));
    }

    @Test
    void externalExecutionResult_mapsCount() {
        ToolResultBlock result = ToolResultBlock.builder()
                .name("run_code")
                .state(ToolResultState.SUCCESS)
                .output(List.of(TextBlock.builder().text("ok").build()))
                .build();
        AgentEvent event = new ExternalExecutionResultEvent("reply-1", List.of(result));
        Map<String, Object> out = mapper.apply(event);

        assertEquals("external_execution_result", out.get("type"));
        assertEquals(1, out.get("count"));
    }

    // ---- hints / subagent / custom ----

    @Test
    void hintBlock_mapsHintAndSource() {
        AgentEvent event = new HintBlockEvent("reply-1", "block-1", "roundtable", "consider cost");
        Map<String, Object> out = mapper.apply(event);

        assertEquals("hint", out.get("type"));
        assertEquals("consider cost", out.get("hint"));
        assertEquals("roundtable", out.get("source"));
    }

    @Test
    void subagentExposed_mapsIdentityFields() {
        AgentEvent event = new SubagentExposedEvent("sub-1", "researcher", "session-1", "Researcher");
        Map<String, Object> out = mapper.apply(event);

        assertEquals("subagent_exposed", out.get("type"));
        assertEquals("sub-1", out.get("subagentId"));
        assertEquals("researcher", out.get("agentId"));
        assertEquals("Researcher", out.get("label"));
    }

    @Test
    void customEvent_usesNameAsTypeAndMergesValue() {
        AgentEvent event = new CustomEvent("my_custom", Map.of("score", 42));
        Map<String, Object> out = mapper.apply(event);

        assertEquals("my_custom", out.get("type"));
        assertEquals(42, out.get("score"));
        assertNotNull(out.get("timestamp"));
    }

    @Test
    void customEvent_nullName_fallsBackToCustomType() {
        AgentEvent event = new CustomEvent((String) null);
        Map<String, Object> out = mapper.apply(event);

        assertEquals("custom", out.get("type"));
    }

    // ---- edge cases ----

    @Test
    void apply_nullEvent_returnsNull() {
        assertNull(mapper.apply(null));
    }

    @Test
    void apply_alwaysIncludesTimestampWhenPresent() {
        AgentEvent event = new ToolCallStartEvent("reply-1", "call-1", "parse_docx");
        Map<String, Object> out = mapper.apply(event);

        assertNotNull(out.get("timestamp"));
        assertTrue((long) out.get("timestamp") > 0);
    }

    @Test
    void apply_toolResultDataDelta_surfacesContentPreview() {
        // Data deltas carry a ContentBlock; the mapper surfaces a string preview so the
        // debug panel can render it uniformly with text tool-result deltas.
        AgentEvent event = new ToolResultDataDeltaEvent("reply-1", "call-1", "search",
                TextBlock.builder().text("hit-1").build());
        Map<String, Object> out = mapper.apply(event);

        assertNotNull(out);
        assertEquals("tool_result_delta", out.get("type"));
        assertNotNull(out.get("content"));
        assertFalse(String.valueOf(out.get("content")).isEmpty());
    }
}

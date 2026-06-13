package com.skloda.agentscope.middleware;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.message.ToolUseBlock;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalMiddlewareTest {

    @Test
    void pendingToolCallsExposeStructuredInputForReadableReview() {
        ApprovalMiddleware middleware = new ApprovalMiddleware(false,
                List.of("generate_contract_review_report"));

        ActingInput input = new ActingInput(List.of(ToolUseBlock.builder()
                .id("call-1")
                .name("generate_contract_review_report")
                .input(Map.of(
                        "contractTitle", "软件开发服务合同",
                        "partyA", "蓝海科技有限公司",
                        "overallRiskLevel", "HIGH"))
                .build()));

        Flux<AgentEvent> result = middleware.onActing(null, null, input, in -> Flux.empty());

        // Approval triggered -> returns empty (skips tool execution)
        assertEquals(0, result.collectList().block().size());

        Map<String, Object> pendingCall = middleware.getPendingToolCallsForSse().get(0);
        assertTrue(middleware.isApprovalTriggered());
        assertEquals("generate_contract_review_report", pendingCall.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inputParams = (Map<String, Object>) pendingCall.get("inputParams");
        assertEquals("软件开发服务合同", inputParams.get("contractTitle"));
        assertEquals("蓝海科技有限公司", inputParams.get("partyA"));
        assertEquals("HIGH", inputParams.get("overallRiskLevel"));
    }

    @Test
    void approvalNotTriggeredWhenNoToolCalls() {
        ApprovalMiddleware middleware = new ApprovalMiddleware(false, List.of("web_search"));
        ActingInput input = new ActingInput(List.of());

        middleware.onActing(null, null, input, in -> Flux.empty()).collectList().block();

        assertFalse(middleware.isApprovalTriggered());
        assertTrue(middleware.getPendingToolUseBlocks().isEmpty());
    }

    @Test
    void approvalNotTriggeredForNonMatchingTool() {
        ApprovalMiddleware middleware = new ApprovalMiddleware(false, List.of("web_search"));
        ActingInput input = new ActingInput(List.of(
                ToolUseBlock.builder().id("t1").name("calculator").input(Map.of()).build()));

        // Non-matching tool should proceed to next (not blocked)
        middleware.onActing(null, null, input, in -> Flux.empty()).collectList().block();

        assertFalse(middleware.isApprovalTriggered());
    }

    @Test
    void approvalTriggeredWhenApprovalRequiredIsTrue() {
        ApprovalMiddleware middleware = new ApprovalMiddleware(true, List.of());
        ActingInput input = new ActingInput(List.of(
                ToolUseBlock.builder().id("t1").name("any_tool").input(Map.of()).build()));

        middleware.onActing(null, null, input, in -> Flux.empty()).collectList().block();

        assertTrue(middleware.isApprovalTriggered());
        assertEquals(1, middleware.getPendingToolUseBlocks().size());
    }

    @Test
    void approvalTriggeredForMatchingTool() {
        ApprovalMiddleware middleware = new ApprovalMiddleware(false,
                List.of("web_search", "delete_file"));
        ActingInput input = new ActingInput(List.of(
                ToolUseBlock.builder().id("t1").name("web_search").input(Map.of("query", "test")).build()));

        middleware.onActing(null, null, input, in -> Flux.empty()).collectList().block();

        assertTrue(middleware.isApprovalTriggered());
    }

    @Test
    void needsApprovalReturnsFalseWhenNothingRequired() {
        ApprovalMiddleware middleware = new ApprovalMiddleware(false, List.of());
        assertFalse(middleware.needsApproval());
    }

    @Test
    void needsApprovalReturnsTrueWhenToolsRequired() {
        ApprovalMiddleware middleware = new ApprovalMiddleware(false, List.of("tool1"));
        assertTrue(middleware.needsApproval());
    }

    @Test
    void needsApprovalReturnsTrueWhenApprovalRequired() {
        ApprovalMiddleware middleware = new ApprovalMiddleware(true, List.of());
        assertTrue(middleware.needsApproval());
    }

    @Test
    void pendingToolCallsForSseWithNullValues() {
        ApprovalMiddleware middleware = new ApprovalMiddleware(true, List.of());
        ActingInput input = new ActingInput(List.of(ToolUseBlock.builder().build()));

        middleware.onActing(null, null, input, in -> Flux.empty()).collectList().block();

        List<Map<String, Object>> calls = middleware.getPendingToolCallsForSse();
        assertEquals(1, calls.size());
        assertEquals("", calls.get(0).get("id"));
        assertEquals("", calls.get(0).get("name"));
    }

    @Test
    void multipleToolCallsAllCaptured() {
        ApprovalMiddleware middleware = new ApprovalMiddleware(true, List.of());
        ActingInput input = new ActingInput(List.of(
                ToolUseBlock.builder().id("t1").name("tool1").input(Map.of()).build(),
                ToolUseBlock.builder().id("t2").name("tool2").input(Map.of()).build()));

        middleware.onActing(null, null, input, in -> Flux.empty()).collectList().block();

        assertEquals(2, middleware.getPendingToolUseBlocks().size());
        assertEquals(2, middleware.getPendingToolCallsForSse().size());
    }

    @Test
    void nullToolCallsListHandled() {
        ApprovalMiddleware middleware = new ApprovalMiddleware(true, List.of());
        ActingInput input = new ActingInput(null);

        // Null toolCalls -> should pass through to next without triggering
        middleware.onActing(null, null, input, in -> Flux.empty()).collectList().block();

        assertFalse(middleware.isApprovalTriggered());
    }

    @Test
    void nullApprovalToolsListHandled() {
        ApprovalMiddleware middleware = new ApprovalMiddleware(false, null);
        assertFalse(middleware.needsApproval());
    }
}

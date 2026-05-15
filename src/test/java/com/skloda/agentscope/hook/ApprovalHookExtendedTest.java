package com.skloda.agentscope.hook;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.message.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalHookExtendedTest {

    private Agent stubAgent;

    @BeforeEach
    void setUp() {
        stubAgent = new StubAgent();
    }

    @Test
    void approvalNotTriggeredWhenNoToolCalls() {
        ApprovalHook hook = new ApprovalHook(false, List.of("web_search"));
        Msg reasoning = Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text("just text").build())
                .build();

        hook.onEvent(new PostReasoningEvent(stubAgent, "model", null, reasoning)).block();

        assertFalse(hook.isApprovalTriggered());
        assertTrue(hook.getPendingToolUseBlocks().isEmpty());
    }

    @Test
    void approvalNotTriggeredForNonMatchingTool() {
        ApprovalHook hook = new ApprovalHook(false, List.of("web_search"));
        Msg reasoning = Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .content(ToolUseBlock.builder().id("t1").name("calculator").input(Map.of()).build())
                .build();

        hook.onEvent(new PostReasoningEvent(stubAgent, "model", null, reasoning)).block();

        assertFalse(hook.isApprovalTriggered());
    }

    @Test
    void approvalTriggeredWhenApprovalRequiredIsTrue() {
        ApprovalHook hook = new ApprovalHook(true, List.of());
        Msg reasoning = Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .content(ToolUseBlock.builder().id("t1").name("any_tool").input(Map.of()).build())
                .build();

        hook.onEvent(new PostReasoningEvent(stubAgent, "model", null, reasoning)).block();

        assertTrue(hook.isApprovalTriggered());
        assertEquals(1, hook.getPendingToolUseBlocks().size());
    }

    @Test
    void approvalTriggeredForMatchingTool() {
        ApprovalHook hook = new ApprovalHook(false, List.of("web_search", "delete_file"));
        Msg reasoning = Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .content(ToolUseBlock.builder().id("t1").name("web_search").input(Map.of("query", "test")).build())
                .build();

        hook.onEvent(new PostReasoningEvent(stubAgent, "model", null, reasoning)).block();

        assertTrue(hook.isApprovalTriggered());
    }

    @Test
    void needsApprovalReturnsFalseWhenNothingRequired() {
        ApprovalHook hook = new ApprovalHook(false, List.of());
        assertFalse(hook.needsApproval());
    }

    @Test
    void needsApprovalReturnsTrueWhenToolsRequired() {
        ApprovalHook hook = new ApprovalHook(false, List.of("tool1"));
        assertTrue(hook.needsApproval());
    }

    @Test
    void needsApprovalReturnsTrueWhenApprovalRequired() {
        ApprovalHook hook = new ApprovalHook(true, List.of());
        assertTrue(hook.needsApproval());
    }

    @Test
    void pendingToolCallsForSseWithNullValues() {
        ApprovalHook hook = new ApprovalHook(true, List.of());
        Msg reasoning = Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .content(ToolUseBlock.builder().build())
                .build();

        hook.onEvent(new PostReasoningEvent(stubAgent, "model", null, reasoning)).block();

        List<Map<String, Object>> calls = hook.getPendingToolCallsForSse();
        assertEquals(1, calls.size());
        assertEquals("", calls.get(0).get("id"));
        assertEquals("", calls.get(0).get("name"));
    }

    @Test
    void multipleToolCallsAllCaptured() {
        ApprovalHook hook = new ApprovalHook(true, List.of());
        Msg reasoning = Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .content(
                        ToolUseBlock.builder().id("t1").name("tool1").input(Map.of()).build(),
                        ToolUseBlock.builder().id("t2").name("tool2").input(Map.of()).build()
                )
                .build();

        hook.onEvent(new PostReasoningEvent(stubAgent, "model", null, reasoning)).block();

        assertEquals(2, hook.getPendingToolUseBlocks().size());
        assertEquals(2, hook.getPendingToolCallsForSse().size());
    }

    @Test
    void priorityIsHigherThanDefault() {
        ApprovalHook hook = new ApprovalHook(false, List.of());
        assertEquals(50, hook.priority());
    }

    @Test
    void nullReasoningMessageDoesNotTrigger() {
        ApprovalHook hook = new ApprovalHook(true, List.of());
        hook.onEvent(new PostReasoningEvent(stubAgent, "model", null, null)).block();

        assertFalse(hook.isApprovalTriggered());
    }

    @Test
    void nullApprovalToolsListHandled() {
        ApprovalHook hook = new ApprovalHook(false, null);
        assertFalse(hook.needsApproval());
    }

    private static class StubAgent implements Agent {
        @Override public String getAgentId() { return "stub"; }
        @Override public String getName() { return "StubAgent"; }
        @Override public void interrupt() {}
        @Override public void interrupt(Msg msg) {}
        @Override public Mono<Msg> call(List<Msg> msgs) { return Mono.empty(); }
        @Override public Mono<Msg> call(List<Msg> msgs, Class<?> cls) { return Mono.empty(); }
        @Override public Mono<Msg> call(List<Msg> msgs, com.fasterxml.jackson.databind.JsonNode schema) { return Mono.empty(); }
        @Override public Flux<Event> stream(List<Msg> msgs, StreamOptions opts) { return Flux.empty(); }
        @Override public Flux<Event> stream(List<Msg> msgs, StreamOptions opts, Class<?> cls) { return Flux.empty(); }
        @Override public Flux<Event> stream(List<Msg> msgs, StreamOptions opts, com.fasterxml.jackson.databind.JsonNode schema) { return Flux.empty(); }
        @Override public Mono<Void> observe(Msg msg) { return Mono.empty(); }
        @Override public Mono<Void> observe(List<Msg> msgs) { return Mono.empty(); }
    }
}

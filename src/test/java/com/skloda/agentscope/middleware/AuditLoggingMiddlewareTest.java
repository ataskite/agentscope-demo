package com.skloda.agentscope.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AuditLoggingMiddlewareTest {

    private final AuditLoggingMiddleware mw = new AuditLoggingMiddleware();
    private final RuntimeContext ctx = RuntimeContext.empty();

    @Test
    void onAgentDelegatesToNext() {
        List<Msg> msgs = List.of(
                Msg.builder().content(TextBlock.builder().text("hello").build()).build()
        );
        AgentInput input = new AgentInput(msgs);
        TextBlockDeltaEvent fakeEvent = new TextBlockDeltaEvent("r1", "b1", "hi");

        Flux<AgentEvent> result = mw.onAgent(
                null, ctx, input,
                in -> Flux.just(fakeEvent)
        );

        List<AgentEvent> events = result.collectList().block();
        assertEquals(1, events.size());
        assertEquals("hi", ((TextBlockDeltaEvent) events.get(0)).getDelta());
    }

    @Test
    void onReasoningDelegatesToNext() {
        ReasoningInput input = new ReasoningInput(List.of(), List.of(), null);
        TextBlockDeltaEvent fakeEvent = new TextBlockDeltaEvent("r1", "b1", "thinking");

        Flux<AgentEvent> result = mw.onReasoning(
                null, ctx, input,
                in -> Flux.just(fakeEvent)
        );

        List<AgentEvent> events = result.collectList().block();
        assertEquals(1, events.size());
    }

    @Test
    void onActingDelegatesToNext() {
        ToolUseBlock toolUse = ToolUseBlock.builder()
                .id("t1").name("web_search").input(Map.of("query", "test")).build();
        ActingInput input = new ActingInput(List.of(toolUse));
        TextBlockDeltaEvent fakeEvent = new TextBlockDeltaEvent("r1", "b1", "result");

        Flux<AgentEvent> result = mw.onActing(
                null, ctx, input,
                in -> Flux.just(fakeEvent)
        );

        List<AgentEvent> events = result.collectList().block();
        assertEquals(1, events.size());
    }
}

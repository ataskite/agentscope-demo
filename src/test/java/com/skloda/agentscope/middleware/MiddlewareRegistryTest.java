package com.skloda.agentscope.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class MiddlewareRegistryTest {

    @Test
    void registerAndCreate() {
        MiddlewareRegistry registry = new MiddlewareRegistry();
        registry.register("test-mw", StubMiddleware::new);
        MiddlewareBase mw = registry.create("test-mw");
        assertNotNull(mw);
        assertTrue(mw instanceof StubMiddleware);
    }

    @Test
    void createUnknownReturnsNull() {
        MiddlewareRegistry registry = new MiddlewareRegistry();
        assertNull(registry.create("nonexistent"));
    }

    @Test
    void listNames() {
        MiddlewareRegistry registry = new MiddlewareRegistry();
        registry.register("a", StubMiddleware::new);
        registry.register("b", StubMiddleware::new);
        assertEquals(2, registry.getRegisteredNames().size());
        assertTrue(registry.getRegisteredNames().contains("a"));
    }

    static class StubMiddleware implements MiddlewareBase {
        @Override
        public Flux<AgentEvent> onAgent(Agent agent, AgentInput input, Function<AgentInput, Flux<AgentEvent>> next) {
            return next.apply(input);
        }

        @Override
        public Flux<AgentEvent> onReasoning(Agent agent, ReasoningInput input, Function<ReasoningInput, Flux<AgentEvent>> next) {
            return next.apply(input);
        }

        @Override
        public Flux<AgentEvent> onActing(Agent agent, ActingInput input, Function<ActingInput, Flux<AgentEvent>> next) {
            return next.apply(input);
        }

        @Override
        public Flux<AgentEvent> onModelCall(Agent agent, ModelCallInput input, Function<ModelCallInput, Flux<AgentEvent>> next) {
            return next.apply(input);
        }

        @Override
        public Mono<String> onSystemPrompt(Agent agent, String prompt) {
            return Mono.just(prompt);
        }
    }
}

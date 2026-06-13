package com.skloda.agentscope.middleware;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ModelCallInput;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitMiddlewareTest {

    private final RateLimitMiddleware mw = new RateLimitMiddleware(3);
    private final RuntimeContext ctx = RuntimeContext.empty();

    @Test
    void allowsUpToLimit() {
        for (int i = 0; i < 3; i++) {
            Flux<AgentEvent> result = mw.onModelCall(null, ctx,
                    new ModelCallInput(List.of(), List.of(), null, null),
                    in -> Flux.empty());
            StepVerifier.create(result).verifyComplete();
        }
    }

    @Test
    void blocksWhenLimitExceeded() {
        // Exhaust the limit
        for (int i = 0; i < 3; i++) {
            mw.onModelCall(null, ctx,
                    new ModelCallInput(List.of(), List.of(), null, null),
                    in -> Flux.empty()).blockLast();
        }

        // Next call should be blocked (next not invoked)
        boolean[] nextCalled = {false};
        Flux<AgentEvent> result = mw.onModelCall(null, ctx,
                new ModelCallInput(List.of(), List.of(), null, null),
                in -> {
                    nextCalled[0] = true;
                    return Flux.empty();
                });

        StepVerifier.create(result).verifyComplete();
        assertFalse(nextCalled[0], "next should not be called when rate limited");
    }
}

package com.skloda.agentscope.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

public class RateLimitMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(RateLimitMiddleware.class);

    private final int maxCallsPerMinute;
    // P3-5: concurrent queue — session agents are shared across concurrent requests and
    // the previous LinkedList corrupted under racing poll/add
    private final Queue<Long> callTimestamps = new ConcurrentLinkedQueue<>();

    public RateLimitMiddleware() {
        this(10);
    }

    public RateLimitMiddleware(int maxCallsPerMinute) {
        this.maxCallsPerMinute = maxCallsPerMinute;
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext ctx, ModelCallInput input,
                                         Function<ModelCallInput, Flux<AgentEvent>> next) {
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000;

        // Remove timestamps outside the 1-minute window
        while (true) {
            Long oldest = callTimestamps.peek();
            if (oldest == null || oldest >= windowStart) {
                break;
            }
            callTimestamps.poll();
        }

        // P3-5: fail loudly instead of Flux.empty(), which used to surface as a silent
        // "empty model reply" and masked the real problem
        int current = callTimestamps.size();
        if (current >= maxCallsPerMinute) {
            log.warn("[rate-limit] Model call blocked — {}/{} calls in last minute",
                    current, maxCallsPerMinute);
            return Flux.error(new IllegalStateException(
                    "Rate limit exceeded: max " + maxCallsPerMinute + " model calls per minute"));
        }

        callTimestamps.add(now);
        log.debug("[rate-limit] Model call allowed — {}/{} in window", current + 1, maxCallsPerMinute);
        return next.apply(input);
    }
}

package com.skloda.agentscope.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Function;

public class RateLimitMiddleware extends MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(RateLimitMiddleware.class);

    private final int maxCallsPerMinute;
    private final Queue<Long> callTimestamps = new LinkedList<>();

    public RateLimitMiddleware() {
        this(10);
    }

    public RateLimitMiddleware(int maxCallsPerMinute) {
        this.maxCallsPerMinute = maxCallsPerMinute;
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, ModelCallInput input,
                                         Function<ModelCallInput, Flux<AgentEvent>> next) {
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000;

        // Remove timestamps outside the 1-minute window
        while (!callTimestamps.isEmpty() && callTimestamps.peek() < windowStart) {
            callTimestamps.poll();
        }

        if (callTimestamps.size() >= maxCallsPerMinute) {
            log.warn("[rate-limit] Model call blocked — {}/{} calls in last minute",
                    callTimestamps.size(), maxCallsPerMinute);
            return Flux.empty();
        }

        callTimestamps.add(now);
        log.debug("[rate-limit] Model call allowed — {}/{} in window",
                callTimestamps.size(), maxCallsPerMinute);
        return next.apply(input);
    }
}

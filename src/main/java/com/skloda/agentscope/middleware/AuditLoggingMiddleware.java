package com.skloda.agentscope.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.*;
import io.agentscope.core.message.ToolUseBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Function;

public class AuditLoggingMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(AuditLoggingMiddleware.class);

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, AgentInput input,
                                     Function<AgentInput, Flux<AgentEvent>> next) {
        String agentName = agent != null ? agent.getName() : "unknown";
        int msgCount = input.msgs() != null ? input.msgs().size() : 0;
        log.info("[audit] Agent '{}' starting with {} input messages", agentName, msgCount);
        long startNanos = System.nanoTime();

        return next.apply(input)
                .doOnComplete(() -> {
                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                    log.info("[audit] Agent '{}' completed in {}ms", agentName, durationMs);
                })
                .doOnError(e -> log.error("[audit] Agent '{}' failed: {}", agentName, e.getMessage()));
    }

    @Override
    public Flux<AgentEvent> onReasoning(Agent agent, ReasoningInput input,
                                         Function<ReasoningInput, Flux<AgentEvent>> next) {
        int msgCount = input.messages() != null ? input.messages().size() : 0;
        log.info("[audit] Reasoning phase starting with {} messages", msgCount);
        long startNanos = System.nanoTime();

        return next.apply(input)
                .doOnComplete(() -> {
                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                    log.info("[audit] Reasoning phase completed in {}ms", durationMs);
                });
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent, ActingInput input,
                                      Function<ActingInput, Flux<AgentEvent>> next) {
        List<ToolUseBlock> toolCalls = input.toolCalls();
        for (ToolUseBlock tool : toolCalls) {
            String params = tool.getInput() != null ? tool.getInput().toString() : "{}";
            String preview = params.length() > 80 ? params.substring(0, 80) + "..." : params;
            log.info("[audit] Tool '{}' called with params: {}", tool.getName(), preview);
        }
        long startNanos = System.nanoTime();

        return next.apply(input)
                .doOnComplete(() -> {
                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                    log.info("[audit] Tool execution completed in {}ms", durationMs);
                });
    }
}

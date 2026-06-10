package com.skloda.agentscope.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.middleware.MiddlewareBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ContextEnrichmentMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(ContextEnrichmentMiddleware.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Mono<String> onSystemPrompt(Agent agent, String currentPrompt) {
        String now = LocalDateTime.now().format(FMT);
        String context = String.format(
                "%s%n%n<context>%n当前时间: %s%n用户身份: demo-user%n</context>",
                currentPrompt, now
        );
        log.debug("[context-enrichment] Injected timestamp: {}", now);
        return Mono.just(context);
    }
}

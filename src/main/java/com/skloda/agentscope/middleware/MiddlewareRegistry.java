package com.skloda.agentscope.middleware;

import io.agentscope.core.middleware.FinalAnswerFilterMiddleware;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Supplier;

@Component
public class MiddlewareRegistry {

    private static final Logger log = LoggerFactory.getLogger(MiddlewareRegistry.class);
    private final Map<String, Supplier<MiddlewareBase>> registry = new LinkedHashMap<>();

    public void register(String name, Supplier<MiddlewareBase> factory) {
        registry.put(name, factory);
        log.debug("Registered middleware: {}", name);
    }

    /**
     * Auto-register all built-in middlewares
     */
    @jakarta.annotation.PostConstruct
    public void registerBuiltInMiddlewares() {
        // Basic middlewares
        register("audit-logging", AuditLoggingMiddleware::new);
        register("rate-limit", RateLimitMiddleware::new);
        register("context-enrichment", ContextEnrichmentMiddleware::new);

        // Enhanced middlewares
        register("detailed-audit", DetailedAuditMiddleware::new);
        register("metrics-collector", MetricsCollectorMiddleware::new);

        // GA built-in: OpenTelemetry tracing (S11)
        register("otel-tracing", OtelTracingMiddleware::new);

        // agentscope 2.0.3 新增：最终答案过滤（抑制 ReAct 流式输出中中间推理轮次的文本，仅输出最终答案）
        register("final-answer-filter", FinalAnswerFilterMiddleware::new);

        log.info("Registered {} built-in middlewares: {}", registry.size(), getRegisteredNames());
    }

    public MiddlewareBase create(String name) {
        Supplier<MiddlewareBase> factory = registry.get(name);
        if (factory == null) {
            log.warn("Middleware not found: {}", name);
            return null;
        }
        return factory.get();
    }

    public List<String> getRegisteredNames() {
        return List.copyOf(registry.keySet());
    }
}

package com.skloda.agentscope.config;

import io.agentscope.core.tracing.TracerRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Initializes OpenTelemetry tracing hook on application startup (S11).
 * <p>
 * Enables Reactor context propagation for OTel spans so that
 * {@link io.agentscope.core.tracing.OtelTracingMiddleware} can correlate
 * trace IDs across the reactive event stream.
 * <p>
 * When no OTel exporter is configured, the {@link io.agentscope.core.tracing.NoopTracer}
 * is used (zero overhead). To enable real tracing, configure an OTLP endpoint
 * via standard OTel system properties or environment variables.
 */
@Component
public class TracingConfig {

    private static final Logger log = LoggerFactory.getLogger(TracingConfig.class);

    @PostConstruct
    public void initTracing() {
        TracerRegistry.enableTracingHook();
        log.info("OpenTelemetry tracing hook enabled (tracer={}, tracerClass={})",
                TracerRegistry.get(),
                TracerRegistry.get().getClass().getSimpleName());
    }
}

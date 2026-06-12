package com.skloda.agentscope.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.middleware.*;
import io.agentscope.core.message.ToolUseBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Metrics collector middleware that aggregates performance statistics.
 *
 * Collects:
 * - Average tool execution time per tool type
 * - Model call frequency and token statistics
 * - Error rate by tool and phase
 * - Cost estimation based on token usage
 * - Agent-level performance summary
 */
public class MetricsCollectorMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(MetricsCollectorMiddleware.class);
    private static final Logger metricsLog = LoggerFactory.getLogger("METRICS");

    // Thread-local metrics context
    private static final ThreadLocal<MetricsContext> METRICS_CONTEXT = new ThreadLocal<>();

    // Global aggregated metrics
    private static final Map<String, ToolMetrics> TOOL_METRICS = new ConcurrentHashMap<>();
    private static final Map<String, AgentMetrics> AGENT_METRICS = new ConcurrentHashMap<>();
    private static final AtomicLong GLOBAL_TOKENS = new AtomicLong(0);
    private static final AtomicLong GLOBAL_CALLS = new AtomicLong(0);
    private static final AtomicLong GLOBAL_ERRORS = new AtomicLong(0);

    // Cost estimation (DashScope qwen-plus pricing as of 2026)
    private static final double INPUT_TOKEN_COST_PER_1K = 0.0008;  // CNY
    private static final double OUTPUT_TOKEN_COST_PER_1K = 0.002;  // CNY

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext ctx, AgentInput input,
                                     Function<AgentInput, Flux<AgentEvent>> next) {
        String agentName = agent != null ? agent.getName() : "unknown";
        MetricsContext context = new MetricsContext(agentName);
        METRICS_CONTEXT.set(context);

        System.out.println("[METRICS-COLLECTOR] Agent start: " + agentName);

        long startNanos = System.nanoTime();

        return next.apply(input)
                .doOnComplete(() -> {
                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

                    // Update agent metrics
                    AgentMetrics agentMetrics = AGENT_METRICS.computeIfAbsent(agentName, k -> new AgentMetrics());
                    agentMetrics.callCount.incrementAndGet();
                    agentMetrics.totalDuration.addAndGet(durationMs);
                    updateMin(agentMetrics.minDuration, durationMs);
                    updateMax(agentMetrics.maxDuration, durationMs);

                    // Log metrics summary to console
                    long tokens = context.tokens.get();
                    double cost = estimateCost(tokens);
                    System.out.println("[METRICS-COLLECTOR] Agent: " + agentName +
                        " | Duration: " + durationMs + "ms" +
                        " | Tools: " + context.toolCalls.get() +
                        " | Tokens: " + tokens +
                        " | Cost: ¥" + String.format("%.4f", cost));

                    // Update global metrics
                    GLOBAL_CALLS.incrementAndGet();

                    METRICS_CONTEXT.remove();
                })
                .doOnError(e -> {
                    GLOBAL_ERRORS.incrementAndGet();
                    System.err.println("[METRICS-COLLECTOR] Agent: " + agentName + " | ERROR: " + e.getMessage());
                    METRICS_CONTEXT.remove();
                });
    }

    @Override
    public Flux<AgentEvent> onReasoning(Agent agent, RuntimeContext ctx, ReasoningInput input,
                                         Function<ReasoningInput, Flux<AgentEvent>> next) {
        MetricsContext context = METRICS_CONTEXT.get();
        long startNanos = System.nanoTime();

        return next.apply(input)
                .doOnComplete(() -> {
                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                    if (context != null) {
                        context.reasoningDuration.addAndGet(durationMs);
                    }
                });
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext ctx, ActingInput input,
                                      Function<ActingInput, Flux<AgentEvent>> next) {
        MetricsContext context = METRICS_CONTEXT.get();
        List<ToolUseBlock> toolCalls = input.toolCalls();

        for (ToolUseBlock tool : toolCalls) {
            String toolName = tool.getName();
            context.currentTool = toolName;
            context.toolCallStart = System.nanoTime();
        }

        return next.apply(input)
                .doOnComplete(() -> {
                    if (context != null && context.currentTool != null) {
                        long durationMs = (System.nanoTime() - context.toolCallStart) / 1_000_000;

                        // Update tool metrics
                        ToolMetrics toolMetrics = TOOL_METRICS.computeIfAbsent(context.currentTool, k -> new ToolMetrics());
                        toolMetrics.callCount.incrementAndGet();
                        toolMetrics.totalDuration.addAndGet(durationMs);
                        updateMin(toolMetrics.minDuration, durationMs);
                        updateMax(toolMetrics.maxDuration, durationMs);

                        context.toolCalls.incrementAndGet();
                        context.currentTool = null;
                    }
                })
                .doOnError(e -> {
                    if (context != null && context.currentTool != null) {
                        ToolMetrics toolMetrics = TOOL_METRICS.computeIfAbsent(context.currentTool, k -> new ToolMetrics());
                        toolMetrics.errorCount.incrementAndGet();
                    }
                });
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext ctx, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        MetricsContext context = METRICS_CONTEXT.get();

        return next.apply(input)
                .doOnNext(event -> {
                    if (context != null && event.getType() == AgentEventType.MODEL_CALL_END && event instanceof ModelCallEndEvent) {
                        ModelCallEndEvent modelEvent = (ModelCallEndEvent) event;
                        if (modelEvent.getUsage() != null) {
                            int tokens = modelEvent.getUsage().getTotalTokens();
                            if (tokens > 0) {
                                context.tokens.addAndGet(tokens);
                                GLOBAL_TOKENS.addAndGet(tokens);
                            }
                        }
                    }
                });
    }

    /**
     * Safely update min value
     */
    private void updateMin(AtomicLong minValue, long newValue) {
        long current = minValue.get();
        while (newValue < current && !minValue.compareAndSet(current, newValue)) {
            current = minValue.get();
        }
    }

    /**
     * Safely update max value
     */
    private void updateMax(AtomicLong maxValue, long newValue) {
        long current = maxValue.get();
        while (newValue > current && !maxValue.compareAndSet(current, newValue)) {
            current = maxValue.get();
        }
    }

    /**
     * Print all aggregated metrics
     */
    public static void printMetricsSummary() {
        metricsLog.info("===== Metrics Summary =====");
        metricsLog.info("Global Statistics:");
        metricsLog.info("  Total Agent Calls: {}", GLOBAL_CALLS.get());
        metricsLog.info("  Total Tokens: {}", GLOBAL_TOKENS.get());
        metricsLog.info("  Total Errors: {}", GLOBAL_ERRORS.get());
        long tokens = GLOBAL_TOKENS.get();
        double cost = estimateCost(tokens);
        metricsLog.info("  Estimated Cost: ¥{}", String.format("%.4f", cost));

        metricsLog.info("Tool Statistics:");
        TOOL_METRICS.forEach((tool, metrics) -> {
            double avgDuration = metrics.callCount.get() > 0
                ? (double) metrics.totalDuration.get() / metrics.callCount.get()
                : 0;
            double errorRate = metrics.callCount.get() > 0
                ? (double) metrics.errorCount.get() / metrics.callCount.get() * 100
                : 0;

            metricsLog.info("  {} - Calls: {}, Avg: {}ms, Min: {}ms, Max: {}ms, Errors: {} ({}%)",
                    tool,
                    metrics.callCount.get(),
                    String.format("%.1f", avgDuration),
                    metrics.minDuration.get(),
                    metrics.maxDuration.get(),
                    metrics.errorCount.get(),
                    String.format("%.1f", errorRate));
        });

        metricsLog.info("Agent Statistics:");
        AGENT_METRICS.forEach((agent, metrics) -> {
            double avgDuration = metrics.callCount.get() > 0
                ? (double) metrics.totalDuration.get() / metrics.callCount.get()
                : 0;

            metricsLog.info("  {} - Calls: {}, Avg: {}ms, Min: {}ms, Max: {}ms",
                    agent,
                    metrics.callCount.get(),
                    String.format("%.1f", avgDuration),
                    metrics.minDuration.get(),
                    metrics.maxDuration.get());
        });
    }

    /**
     * Reset all metrics
     */
    public static void resetMetrics() {
        TOOL_METRICS.clear();
        AGENT_METRICS.clear();
        GLOBAL_TOKENS.set(0);
        GLOBAL_CALLS.set(0);
        GLOBAL_ERRORS.set(0);
        metricsLog.info("Metrics reset");
    }

    /**
     * Estimate cost based on token usage
     * Assumes 50% input, 50% output (rough estimate)
     */
    private static double estimateCost(long tokens) {
        long inputTokens = tokens / 2;
        long outputTokens = tokens - inputTokens;
        return (inputTokens / 1000.0) * INPUT_TOKEN_COST_PER_1K
             + (outputTokens / 1000.0) * OUTPUT_TOKEN_COST_PER_1K;
    }

    /**
     * Safely get int value from various types
     */
    private static int getIntValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Thread-local metrics context
     */
    private static class MetricsContext {
        final String agentName;
        final AtomicLong reasoningDuration = new AtomicLong(0);
        final AtomicInteger toolCalls = new AtomicInteger(0);
        final AtomicLong tokens = new AtomicLong(0);
        String currentTool;
        long toolCallStart;

        MetricsContext(String agentName) {
            this.agentName = agentName;
        }
    }

    /**
     * Tool-level metrics
     */
    private static class ToolMetrics {
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicLong totalDuration = new AtomicLong(0);
        AtomicLong minDuration = new AtomicLong(Long.MAX_VALUE);
        AtomicLong maxDuration = new AtomicLong(0);
        AtomicInteger errorCount = new AtomicInteger(0);
    }

    /**
     * Agent-level metrics
     */
    private static class AgentMetrics {
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicLong totalDuration = new AtomicLong(0);
        AtomicLong minDuration = new AtomicLong(Long.MAX_VALUE);
        AtomicLong maxDuration = new AtomicLong(0);
    }
}

package com.skloda.agentscope.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockEndEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.middleware.*;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Enhanced audit middleware that provides detailed execution transparency.
 *
 * Captures:
 * - Complete execution chain with trace ID
 * - Tool call inputs and outputs
 * - Thinking content and reasoning rounds
 * - Token usage and cost estimation
 * - Error details with stack traces
 */
public class DetailedAuditMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(DetailedAuditMiddleware.class);

    // Thread-local storage for trace context
    private static final ThreadLocal<TraceContext> TRACE_CONTEXT = new ThreadLocal<>();

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, AgentInput input,
                                     Function<AgentInput, Flux<AgentEvent>> next) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        String agentName = agent != null ? agent.getName() : "unknown";
        int msgCount = input.msgs() != null ? input.msgs().size() : 0;

        // Initialize trace context
        TraceContext context = new TraceContext(traceId, agentName);
        TRACE_CONTEXT.set(context);

        log.info("[audit] ===== Agent Execution Start =====");
        log.info("[audit] Trace ID: {}", traceId);
        log.info("[audit] Agent: {}", agentName);
        log.info("[audit] Input: {} message(s)", msgCount);
        log.info("[audit] Time: {}", java.time.LocalDateTime.now());

        // Log input messages preview
        if (input.msgs() != null && !input.msgs().isEmpty()) {
            for (int i = 0; i < input.msgs().size(); i++) {
                Msg msg = input.msgs().get(i);
                String preview = msg.toString();
                if (preview.length() > 100) {
                    preview = preview.substring(0, 100) + "...";
                }
                log.info("[audit]   Message[{}]: {}", i + 1, preview);
            }
        }

        long startNanos = System.nanoTime();

        return next.apply(input)
                .doOnNext(event -> {
                    // Capture detailed event information
                    if (event.getType() == AgentEventType.TOOL_CALL_START) {
                        context.toolCalls.incrementAndGet();
                    } else if (event.getType() == AgentEventType.THINKING_BLOCK_END) {
                        context.thinkingBlocks.incrementAndGet();
                    } else if (event.getType() == AgentEventType.TEXT_BLOCK_END) {
                        context.textBlocks.incrementAndGet();
                    }
                })
                .doOnComplete(() -> {
                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                    log.info("[audit] ===== Agent Execution End =====");
                    log.info("[audit] Trace ID: {}", traceId);
                    log.info("[audit] Total duration: {}ms", durationMs);
                    log.info("[audit] Total reasoning rounds: {}", context.reasoningRounds.get());
                    log.info("[audit] Total tool calls: {}", context.toolCalls.get());
                    log.info("[audit] Total thinking blocks: {}", context.thinkingBlocks.get());
                    log.info("[audit] Total text blocks: {}", context.textBlocks.get());
                    log.info("[audit] Success: true");
                    TRACE_CONTEXT.remove();
                })
                .doOnError(e -> {
                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                    log.error("[audit] ===== Agent Execution End (ERROR) =====");
                    log.error("[audit] Trace ID: {}", traceId);
                    log.error("[audit] Total duration: {}ms", durationMs);
                    log.error("[audit] Error: {}", e.getMessage(), e);
                    log.error("[audit] Success: false");
                    TRACE_CONTEXT.remove();
                });
    }

    @Override
    public Flux<AgentEvent> onReasoning(Agent agent, ReasoningInput input,
                                         Function<ReasoningInput, Flux<AgentEvent>> next) {
        TraceContext context = TRACE_CONTEXT.get();
        if (context != null) {
            context.reasoningRounds.incrementAndGet();
            log.info("[audit] ----- Reasoning Round {} -----", context.reasoningRounds.get());

            // Log model info if available
            if (input.options() != null) {
                String model = input.options().getModelName();
                if (model != null) {
                    log.info("[audit] Model: {}", model);
                }
            }

            // Log tool count available
            int toolCount = input.tools() != null ? input.tools().size() : 0;
            log.info("[audit] Tools available: {}", toolCount);

            // Log message count
            int msgCount = input.messages() != null ? input.messages().size() : 0;
            log.info("[audit] Input messages: {}", msgCount);
        }

        long startNanos = System.nanoTime();

        return next.apply(input)
                .doOnNext(event -> {
                    if (context != null) {
                        // Capture thinking content
                        if (event.getType() == AgentEventType.THINKING_BLOCK_DELTA && event instanceof ThinkingBlockDeltaEvent) {
                            ThinkingBlockDeltaEvent deltaEvent = (ThinkingBlockDeltaEvent) event;
                            String delta = deltaEvent.getDelta();
                            if (delta != null && !delta.isEmpty()) {
                                context.thinkingContent.append(delta);
                            }
                        } else if (event.getType() == AgentEventType.THINKING_BLOCK_END) {
                            if (context.thinkingContent.length() > 0) {
                                String thinking = context.thinkingContent.toString();
                                String preview = thinking.length() > 200 ? thinking.substring(0, 200) + "..." : thinking;
                                log.info("[audit] Thinking: {}", preview);
                                context.thinkingContent.setLength(0);
                            }
                        }

                        // Capture text output
                        if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA && event instanceof TextBlockDeltaEvent) {
                            TextBlockDeltaEvent deltaEvent = (TextBlockDeltaEvent) event;
                            String delta = deltaEvent.getDelta();
                            if (delta != null && !delta.isEmpty()) {
                                context.textContent.append(delta);
                            }
                        } else if (event.getType() == AgentEventType.TEXT_BLOCK_END) {
                            if (context.textContent.length() > 0) {
                                String text = context.textContent.toString();
                                String preview = text.length() > 100 ? text.substring(0, 100) + "..." : text;
                                log.info("[audit] Output: {}", preview);
                                context.textContent.setLength(0);
                            }
                        }
                    }
                })
                .doOnComplete(() -> {
                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                    if (context != null) {
                        log.info("[audit] Reasoning duration: {}ms", durationMs);
                    }
                });
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent, ActingInput input,
                                      Function<ActingInput, Flux<AgentEvent>> next) {
        TraceContext context = TRACE_CONTEXT.get();
        List<ToolUseBlock> toolCalls = input.toolCalls();

        for (int i = 0; i < toolCalls.size(); i++) {
            ToolUseBlock tool = toolCalls.get(i);
            String toolName = tool.getName();
            Map<String, Object> params = tool.getInput();

            log.info("[audit] ----- Tool Execution {} -----", i + 1);
            log.info("[audit] Tool: {}", toolName);
            log.info("[audit] Call ID: {}", tool.getId());

            // Log parameters with proper formatting
            if (params != null && !params.isEmpty()) {
                String paramsStr = formatMap(params);
                String preview = paramsStr.length() > 150 ? paramsStr.substring(0, 150) + "..." : paramsStr;
                log.info("[audit] Params: {}", preview);
            } else {
                log.info("[audit] Params: {{}}");
            }

            // Store tool call info for result logging
            if (context != null) {
                context.currentToolName = toolName;
                context.currentToolId = tool.getId();
            }
        }

        long startNanos = System.nanoTime();

        return next.apply(input)
                .doOnNext(event -> {
                    if (context != null && event.getType() == AgentEventType.TOOL_RESULT_TEXT_DELTA && event instanceof ToolResultTextDeltaEvent) {
                        ToolResultTextDeltaEvent deltaEvent = (ToolResultTextDeltaEvent) event;
                        String delta = deltaEvent.getDelta();
                        if (delta != null && !delta.isEmpty()) {
                            context.toolResultContent.append(delta);
                        }
                    } else if (context != null && event.getType() == AgentEventType.TOOL_RESULT_END) {
                        if (context.toolResultContent.length() > 0) {
                            String result = context.toolResultContent.toString();
                            String preview = result.length() > 100 ? result.substring(0, 100) + "..." : result;
                            log.info("[audit] Result: {}", preview);
                            context.toolResultContent.setLength(0);
                        }
                    }
                })
                .doOnComplete(() -> {
                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                    if (context != null) {
                        log.info("[audit] Tool execution duration: {}ms", durationMs);
                    }
                })
                .doOnError(e -> {
                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                    log.error("[audit] Tool execution failed after {}ms: {}", durationMs, e.getMessage(), e);
                });
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        TraceContext context = TRACE_CONTEXT.get();

        return next.apply(input)
                .doOnNext(event -> {
                    if (context != null && event.getType() == AgentEventType.MODEL_CALL_END && event instanceof ModelCallEndEvent) {
                        ModelCallEndEvent modelEvent = (ModelCallEndEvent) event;
                        if (modelEvent.getUsage() != null) {
                            int tokens = modelEvent.getUsage().getTotalTokens();
                            context.totalTokens.addAndGet(tokens);
                        }
                    }
                });
    }

    /**
     * Format a map as JSON-like string
     */
    private String formatMap(Map<String, Object> map) {
        if (map == null) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(entry.getKey()).append("=");
            Object value = entry.getValue();
            if (value instanceof String) {
                String strVal = (String) value;
                if (strVal.length() > 50) {
                    sb.append("\"").append(strVal.substring(0, 50)).append("...\"");
                } else {
                    sb.append("\"").append(strVal).append("\"");
                }
            } else {
                sb.append(value != null ? value.toString() : "null");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Safely get int value from various types
     */
    private int getIntValue(Object value) {
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
     * Thread-local trace context for tracking execution details
     */
    private static class TraceContext {
        final String traceId;
        final String agentName;
        final AtomicInteger reasoningRounds = new AtomicInteger(0);
        final AtomicInteger toolCalls = new AtomicInteger(0);
        final AtomicInteger thinkingBlocks = new AtomicInteger(0);
        final AtomicInteger textBlocks = new AtomicInteger(0);
        final AtomicLong totalTokens = new AtomicLong(0);
        final StringBuilder thinkingContent = new StringBuilder();
        final StringBuilder textContent = new StringBuilder();
        final StringBuilder toolResultContent = new StringBuilder();
        String currentToolName;
        String currentToolId;

        TraceContext(String traceId, String agentName) {
            this.traceId = traceId;
            this.agentName = agentName;
        }
    }
}

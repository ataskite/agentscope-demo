package com.skloda.agentscope.runtime;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Shared helpers for the hand-rolled multi-agent runtimes (sequential, parallel, debate,
 * loop, msghub). Centralizes two concerns that were previously copy-pasted across 7 files:
 *
 * <ul>
 *   <li><b>{@link #extractText(Msg)}</b> — uniform text extraction from a {@link Msg}.</li>
 *   <li><b>{@link #runSubAgent}</b> — the bridge between "streaming" and "orchestration":
 *       it runs a sub-agent via {@code streamEvents()} so every lifecycle event
 *       (thinking, tool_start, tool_end, text deltas, …) is forwarded as SSE to the
 *       frontend {@link FluxSink}, <em>and</em> resolves to the sub-agent's final text via
 *       a {@link Mono} so the caller can chain the next step. This replaces the old
 *       {@code agent.call()} pattern which returned only the final {@link Msg} and dropped
 *       all streaming events.</li>
 * </ul>
 *
 * <p>The final text is recovered from an {@link AgentResultEvent} when present, falling back
 * to accumulated {@code text} deltas — so orchestration works even if the framework does not
 * emit an AGENT_RESULT event for a given sub-agent.
 */
public final class MultiAgentStreamSupport {

    private static final AgentEventMapper EVENT_MAPPER = new AgentEventMapper();

    private MultiAgentStreamSupport() {
    }

    /**
     * Extract concatenated text from a message's content blocks.
     */
    public static String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb && tb.getText() != null) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    /**
     * Run a sub-agent, forwarding every mapped {@link AgentEvent} as an SSE payload to
     * {@code sink} (tagged with {@code sourceLabel} so the frontend can attribute events to
     * this sub-agent), and resolve to the sub-agent's final text.
     *
     * <p>This is the synchronous point for chained orchestration: the caller can
     * {@code .flatMap(finalText -> nextStep(...))} on the returned {@link Mono}.
     *
     * @param agent       the sub-agent to invoke
     * @param msg         the input message (must not be null — streamEvents has no 0-arg form)
     * @param sourceLabel attribution label stamped onto each forwarded SSE event
     * @param sink        the outer pipeline SSE sink to forward events into
     * @param onError     invoked if the sub-agent stream errors
     * @return a Mono resolving to the sub-agent's final text
     */
    public static Mono<String> runSubAgent(ReActAgent agent, Msg msg, String sourceLabel,
                                           FluxSink<Map<String, Object>> sink,
                                           BiConsumer<String, Throwable> onError) {
        Flux<AgentEvent> events = agent.streamEvents(msg);
        // Capture the final text: prefer AGENT_RESULT's Msg, fall back to accumulated text deltas.
        AtomicReference<String> finalText = new AtomicReference<>("");
        AtomicReference<String> accumulatedText = new AtomicReference<>("");

        return events
                .doOnNext(event -> {
                    // Forward every mapped event to the pipeline sink with a source tag.
                    Map<String, Object> mapped = EVENT_MAPPER.apply(event);
                    if (mapped != null && !mapped.isEmpty()) {
                        // Re-wrap as a mutable map so we can stamp the source label
                        // (the mapper may return immutable Map.of(...) for simple events).
                        Map<String, Object> withSource = new java.util.LinkedHashMap<>(mapped);
                        withSource.put("source", sourceLabel);
                        sink.next(withSource);
                        // Track text deltas as a fallback in case no AGENT_RESULT is emitted.
                        if ("text".equals(withSource.get("type"))
                                && withSource.get("content") instanceof String s) {
                            accumulatedText.set(accumulatedText.get() + s);
                        }
                    }
                    // Prefer the authoritative final result when available.
                    if (event instanceof AgentResultEvent are && are.getResult() != null) {
                        String t = extractText(are.getResult());
                        if (!t.isEmpty()) {
                            finalText.set(t);
                        }
                    }
                })
                .then(Mono.fromSupplier(() -> {
                    String ft = finalText.get();
                    return ft.isEmpty() ? accumulatedText.get() : ft;
                }))
                .onErrorResume(error -> {
                    if (onError != null) {
                        onError.accept(sourceLabel, error);
                    }
                    sink.next(Map.of("type", "error", "message",
                            sourceLabel + " error: " + error.getMessage()));
                    return Mono.just("");
                });
    }
}

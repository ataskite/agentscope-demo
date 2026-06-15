package com.skloda.agentscope.runtime;

import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.*;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Loop runtime — iterative write-review-refine pattern.
 * Writer produces content, critic reviews, loop continues until approved or max iterations.
 *
 * <p>Each sub-agent runs via {@code streamEvents()} (bridged by {@link MultiAgentStreamSupport}),
 * so the frontend receives every thinking/tool/text delta from both the writer and the critic.
 *
 * <p>Bug fixes vs the prior {@code agent.call()} version:
 * <ul>
 *   <li>The {@code max-iterations} exit path now emits the last writer output as a {@code text}
 *       event (previously dropped entirely — the user saw no final content on exhaustion).</li>
 *   <li>Nested {@code .subscribe()} inside {@code Flux.create} (writer→critic) replaced by a
 *       {@code Mono} {@code flatMap} chain, eliminating fire-and-forget race conditions.</li>
 * </ul>
 */
public class LoopRuntime implements StreamingAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(LoopRuntime.class);
    private static final java.util.Set<String> APPROVAL_KEYWORDS = java.util.Set.of(
            "通过", "APPROVED", "approved", "通过审核", "LGTM", "lgtm", "认可", "合格"
    );

    @Getter
    private final ObservabilityHook hook;
    private final ReActAgent writer;
    private final ReActAgent critic;
    private final String pipelineId;
    private final int maxIterations;

    public LoopRuntime(ReActAgent writer, ReActAgent critic,
                       ObservabilityHook hook, String pipelineId, int maxIterations) {
        this.writer = writer;
        this.critic = critic;
        this.hook = hook;
        this.pipelineId = pipelineId;
        this.maxIterations = maxIterations;
    }

    @Override
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        Flux<Map<String, Object>> sinkEvents = hook.getEventSink().asFlux();

        Flux<Map<String, Object>> loopFlux = Flux.create(fluxSink -> {
            hook.emitLoopStart(0);
            String originalRequest = MultiAgentStreamSupport.extractText(userMsg);

            iterate(originalRequest, "", 0, fluxSink)
                    .subscribe();  // single subscription at the top of the chain
        });

        // doFinally completes the EventSink so Flux.merge(sinkEvents, loopFlux) can terminate.
        return Flux.merge(sinkEvents, loopFlux.doFinally(s -> close()))
                .doOnCancel(this::close);
    }

    /**
     * One write→review iteration, returning a {@link Mono} that completes when the loop
     * terminates (approved or max iterations). Replaces the old recursive nested-subscribe.
     */
    private Mono<Void> iterate(String originalRequest, String previousFeedback, int iteration,
                               reactor.core.publisher.FluxSink<Map<String, Object>> fluxSink) {
        // Max iterations reached: emit the last writer output (BUG FIX — was dropped) and finish.
        if (iteration >= maxIterations) {
            // We have no new writer output here; the previous iteration's writer output is the
            // best final content. The caller (approved/else branch) emits text before recursing,
            // so on the max-iter boundary we emit a final "best-effort" note only if we never
            // emitted one. To keep it simple and correct, finish without a duplicate text here.
            return finishLoop(iteration, false, "达到最大迭代次数", fluxSink);
        }

        // Writer step
        String writerPrompt = buildWriterPrompt(originalRequest, previousFeedback, iteration);
        Msg writerMsg = Msg.builder().name("user").role(MsgRole.USER).textContent(writerPrompt).build();

        return MultiAgentStreamSupport.runSubAgent(writer, writerMsg, writer.getName(), fluxSink, null)
                .flatMap(writerOutput -> {
                    fluxSink.next(Map.of("type", "loop_writer_output",
                            "iteration", iteration, "content", writerOutput));

                    // Critic step
                    String criticPrompt = buildCriticPrompt(originalRequest, writerOutput);
                    Msg criticMsg = Msg.builder().name("user").role(MsgRole.USER).textContent(criticPrompt).build();

                    return MultiAgentStreamSupport.runSubAgent(critic, criticMsg, critic.getName(), fluxSink, null)
                            .flatMap(criticOutput -> {
                                boolean approved = containsApproval(criticOutput);

                                hook.emitLoopIterationResult(iteration, approved, criticOutput);
                                fluxSink.next(Map.of("type", "loop_iteration_result",
                                        "iteration", iteration, "approved", approved,
                                        "feedback", criticOutput));

                                if (approved) {
                                    // Approved: emit the writer's output as the final text.
                                    fluxSink.next(Map.of("type", "text", "content", writerOutput));
                                    return finishLoop(iteration + 1, true, criticOutput, fluxSink);
                                }
                                // Keep the last writer output on hand so the max-iter path can emit it.
                                lastWriterOutput = writerOutput;
                                return iterate(originalRequest, criticOutput, iteration + 1, fluxSink);
                            });
                });
    }

    // Holds the most recent writer output so the max-iterations exit path can emit it.
    // Set on every non-approved iteration; read once when iteration budget is exhausted.
    private volatile String lastWriterOutput = "";

    private Mono<Void> finishLoop(int totalIterations, boolean approved, String feedback,
                                  reactor.core.publisher.FluxSink<Map<String, Object>> fluxSink) {
        // BUG FIX: on the max-iterations path, emit the last writer output as the final text
        // so the user sees content even when the critic never approved.
        if (!approved && lastWriterOutput != null && !lastWriterOutput.isEmpty()) {
            fluxSink.next(Map.of("type", "text", "content", lastWriterOutput));
        }
        hook.emitLoopEnd(totalIterations, approved);
        fluxSink.next(Map.of("type", "loop_end",
                "totalIterations", totalIterations, "approved", approved));
        fluxSink.next(Map.of("type", "done"));
        fluxSink.complete();
        return Mono.empty();
    }

    private String buildWriterPrompt(String originalRequest, String feedback, int iteration) {
        if (iteration == 0) {
            return "请根据以下要求撰写内容：\n\n" + originalRequest;
        }
        return "请根据以下原始要求和你之前收到的修改意见，重新撰写内容。\n\n" +
                "## 原始要求\n" + originalRequest + "\n\n" +
                "## 修改意见\n" + feedback + "\n\n" +
                "请撰写修改后的版本。";
    }

    private String buildCriticPrompt(String originalRequest, String writerOutput) {
        return "请审核以下内容是否符合原始要求。如果内容合格，请回复“通过”或“APPROVED”。\n\n" +
                "## 原始要求\n" + originalRequest + "\n\n" +
                "## 待审核内容\n" + writerOutput + "\n\n" +
                "请给出你的评审意见。如果需要修改，请说明具体修改建议。";
    }

    private boolean containsApproval(String text) {
        if (text == null) return false;
        for (String keyword : APPROVAL_KEYWORDS) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    @Override
    public void close() {
        hook.getEventSink().complete();
    }
}

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
            String originalRequest = extractText(userMsg);
            iterate(originalRequest, "", 0, fluxSink);
        });

        return Flux.merge(sinkEvents, loopFlux).doOnCancel(this::close);
    }

    private void iterate(String originalRequest, String previousFeedback, int iteration,
                         reactor.core.publisher.FluxSink<Map<String, Object>> fluxSink) {
        if (iteration >= maxIterations) {
            finishLoop(iteration, false, "达到最大迭代次数", fluxSink);
            return;
        }

        // Writer step
        String writerPrompt = buildWriterPrompt(originalRequest, previousFeedback, iteration);
        Msg writerMsg = Msg.builder().name("user").role(MsgRole.USER).textContent(writerPrompt).build();

        writer.call(writerMsg).subscribe(writerResponse -> {
            String writerOutput = extractText(writerResponse);
            fluxSink.next(Map.of("type", "loop_writer_output",
                    "iteration", iteration, "content", writerOutput));

            // Critic step
            String criticPrompt = buildCriticPrompt(originalRequest, writerOutput);
            Msg criticMsg = Msg.builder().name("user").role(MsgRole.USER).textContent(criticPrompt).build();

            critic.call(criticMsg).subscribe(criticResponse -> {
                String criticOutput = extractText(criticResponse);
                boolean approved = containsApproval(criticOutput);

                hook.emitLoopIterationResult(iteration, approved, criticOutput);
                fluxSink.next(Map.of("type", "loop_iteration_result",
                        "iteration", iteration, "approved", approved,
                        "feedback", criticOutput));

                if (approved) {
                    fluxSink.next(Map.of("type", "text", "content", writerOutput));
                    finishLoop(iteration + 1, true, criticOutput, fluxSink);
                } else {
                    iterate(originalRequest, criticOutput, iteration + 1, fluxSink);
                }
            }, error -> {
                log.error("Critic error at iteration {}", iteration, error);
                fluxSink.next(Map.of("type", "error", "message", "Critic error: " + error.getMessage()));
                fluxSink.next(Map.of("type", "done"));
                fluxSink.complete();
            });
        }, error -> {
            log.error("Writer error at iteration {}", iteration, error);
            fluxSink.next(Map.of("type", "error", "message", "Writer error: " + error.getMessage()));
            fluxSink.next(Map.of("type", "done"));
            fluxSink.complete();
        });
    }

    private void finishLoop(int totalIterations, boolean approved, String feedback,
                            reactor.core.publisher.FluxSink<Map<String, Object>> fluxSink) {
        hook.emitLoopEnd(totalIterations, approved);
        fluxSink.next(Map.of("type", "loop_end",
                "totalIterations", totalIterations, "approved", approved));
        fluxSink.next(Map.of("type", "done"));
        fluxSink.complete();
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

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb) sb.append(tb.getText());
        }
        return sb.toString();
    }

    @Override
    public void close() {
        hook.getEventSink().complete();
    }
}

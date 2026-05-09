package com.skloda.agentscope.composite.pipeline;

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.pipeline.Pipeline;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Loop pipeline implementing write-review-revise pattern.
 * Writer produces content, Critic reviews it. Loop until approved or max iterations reached.
 */
public class LoopPipeline implements Pipeline<Msg> {

    private final AgentBase writer;
    private final AgentBase critic;
    private final int maxIterations;
    private final boolean autoExit;
    private final List<BiConsumer<String, Map<String, Object>>> eventConsumers = new ArrayList<>();

    public LoopPipeline(AgentBase writer, AgentBase critic, int maxIterations, boolean autoExit) {
        this.writer = writer;
        this.critic = critic;
        this.maxIterations = maxIterations;
        this.autoExit = autoExit;
    }

    public void addEventConsumer(BiConsumer<String, Map<String, Object>> consumer) {
        eventConsumers.add(consumer);
    }

    @Override
    public Mono<Msg> execute(Msg input) {
        return execute(input, null);
    }

    @Override
    public Mono<Msg> execute(Msg input, Class<?> structuredOutputClass) {
        return loopIteration(input, 1, null);
    }

    private Mono<Msg> loopIteration(Msg currentInput, int iteration, String previousFeedback) {
        if (iteration > maxIterations) {
            return Mono.justOrEmpty(currentInput);
        }

        emit("loop_start", Map.of("iteration", iteration));

        Msg writerMsg = buildWriterMessage(currentInput, previousFeedback, iteration == 1);
        return writer.call(writerMsg)
                .flatMap(writerOutput -> {
                    String writerText = extractText(writerOutput);
                    emit("pipeline_step_end", Map.of("agentId", "writer", "iteration", iteration));

                    Msg criticMsg = buildCriticMessage(writerText);
                    return critic.call(criticMsg)
                            .flatMap(criticOutput -> {
                                String criticText = extractText(criticOutput);
                                emit("pipeline_step_end", Map.of("agentId", "critic", "iteration", iteration));

                                boolean approved = autoExit && containsApproval(criticText);
                                emit("loop_iteration_result", Map.of(
                                        "iteration", iteration,
                                        "approved", approved,
                                        "feedback", truncate(criticText, 200)
                                ));

                                if (approved) {
                                    emit("loop_end", Map.of(
                                            "totalIterations", iteration,
                                            "finalApproved", true
                                    ));
                                    return Mono.just(buildFinalOutput(writerText, criticText));
                                } else if (iteration >= maxIterations) {
                                    emit("loop_end", Map.of(
                                            "totalIterations", iteration,
                                            "finalApproved", false
                                    ));
                                    return Mono.just(buildFinalOutput(writerText, criticText));
                                } else {
                                    return loopIteration(currentInput, iteration + 1, criticText);
                                }
                            });
                });
    }

    private Msg buildWriterMessage(Msg originalInput, String feedback, boolean isFirst) {
        StringBuilder sb = new StringBuilder();
        if (originalInput != null && originalInput.getContent() != null) {
            for (ContentBlock block : originalInput.getContent()) {
                if (block instanceof TextBlock tb) {
                    sb.append(tb.getText());
                }
            }
        }
        if (!isFirst && feedback != null) {
            sb.append("\n\n---\n\n## 评审意见\n\n").append(feedback);
            sb.append("\n\n请根据以上评审意见修改你的内容。保留原文的核心内容，针对评审中指出的问题进行改进。");
        }
        return Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(sb.toString()).build())
                .build();
    }

    private Msg buildCriticMessage(String writerOutput) {
        String prompt = "请评审以下内容。如果你认为内容已经足够好，请在回复中包含【通过】。\n" +
                "如果需要修改，请给出具体的修改建议。\n\n" +
                "---\n\n" + writerOutput;
        return Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(prompt).build())
                .build();
    }

    private boolean containsApproval(String text) {
        if (text == null) return false;
        return text.contains("通过") || text.contains("APPROVED") || text.contains("approved");
    }

    private Msg buildFinalOutput(String writerText, String criticText) {
        String finalContent = writerText + "\n\n---\n\n**评审总结:**\n" + truncate(criticText, 500);
        return Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text(finalContent).build())
                .build();
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private void emit(String type, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>(data);
        payload.put("type", type);
        for (var consumer : eventConsumers) {
            consumer.accept(type, payload);
        }
    }
}
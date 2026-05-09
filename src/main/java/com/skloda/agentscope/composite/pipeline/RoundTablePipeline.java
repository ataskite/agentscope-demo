package com.skloda.agentscope.composite.pipeline;

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.pipeline.Pipeline;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Expert roundtable pipeline using sequential multi-round discussion.
 * Each round, all experts speak in order, seeing previous messages.
 * After all rounds, moderator synthesizes a summary.
 */
public class RoundTablePipeline implements Pipeline<Msg> {

    private final AgentBase moderator;
    private final List<AgentBase> experts;
    private final int rounds;
    private final List<BiConsumer<String, Map<String, Object>>> eventConsumers = new ArrayList<>();

    public RoundTablePipeline(AgentBase moderator, List<AgentBase> experts, int rounds) {
        this.moderator = moderator;
        this.experts = experts;
        this.rounds = rounds;
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
        List<String> participantNames = new ArrayList<>();
        for (AgentBase e : experts) {
            participantNames.add(e.getName() != null ? e.getName() : e.getClass().getSimpleName());
        }

        emit("roundtable_start", Map.of(
                "participants", participantNames,
                "rounds", rounds
        ));

        return runRounds(input, 1, new ArrayList<>())
                .flatMap(allDiscussion -> {
                    Msg summaryMsg = buildSummaryMessage(input, allDiscussion);
                    return moderator.call(summaryMsg)
                            .map(summary -> {
                                String summaryText = extractText(summary);
                                emit("roundtable_summary", Map.of(
                                        "agent", moderator.getName() != null ? moderator.getName() : "moderator",
                                        "content", truncate(summaryText, 500)
                                ));
                                return summary;
                            });
                });
    }

    private Mono<List<String>> runRounds(Msg originalInput, int currentRound, List<String> allDiscussion) {
        if (currentRound > rounds) {
            return Mono.just(allDiscussion);
        }

        emit("round_start", Map.of("round", currentRound));

        return runExpertRound(originalInput, currentRound, allDiscussion, 0)
                .flatMap(updatedDiscussion -> {
                    emit("round_end", Map.of("round", currentRound));
                    return runRounds(originalInput, currentRound + 1, updatedDiscussion);
                });
    }

    private Mono<List<String>> runExpertRound(Msg originalInput, int round, List<String> discussion, int expertIndex) {
        if (expertIndex >= experts.size()) {
            return Mono.just(discussion);
        }

        AgentBase expert = experts.get(expertIndex);
        String expertName = expert.getName() != null ? expert.getName() : ("expert-" + expertIndex);

        Msg expertMsg = buildExpertMessage(originalInput, round, discussion);
        return expert.call(expertMsg)
                .map(response -> {
                    String responseText = extractText(response);
                    emit("round_message", Map.of(
                            "agent", expertName,
                            "content", truncate(responseText, 300)
                    ));
                    List<String> updated = new ArrayList<>(discussion);
                    updated.add("**" + expertName + " (第" + round + "轮):**\n" + responseText);
                    return updated;
                })
                .flatMap(updated -> runExpertRound(originalInput, round, updated, expertIndex + 1));
    }

    private Msg buildExpertMessage(Msg originalInput, int round, List<String> discussion) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 评审主题\n\n");
        for (ContentBlock block : originalInput.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            }
        }

        if (!discussion.isEmpty()) {
            sb.append("\n\n## 历史讨论\n\n");
            for (String msg : discussion) {
                sb.append(msg).append("\n\n");
            }
        }

        sb.append("\n\n---\n\n这是第 ").append(round).append(" 轮讨论。");
        if (round == 1) {
            sb.append("请提出你的专业观点。");
        } else if (round < rounds) {
            sb.append("请回应其他专家的观点，进一步深入讨论。");
        } else {
            sb.append("最后一轮，请尝试达成共识或总结你的核心观点。");
        }

        return Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(sb.toString()).build())
                .build();
    }

    private Msg buildSummaryMessage(Msg originalInput, List<String> allDiscussion) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 评审主题\n\n");
        for (ContentBlock block : originalInput.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            }
        }
        sb.append("\n\n## 所有讨论记录\n\n");
        for (String msg : allDiscussion) {
            sb.append(msg).append("\n\n");
        }
        sb.append("---\n\n请综合所有专家的观点，给出最终的评审报告，包括：\n");
        sb.append("1. 各专家核心观点摘要\n");
        sb.append("2. 共识与分歧\n");
        sb.append("3. 最终建议");

        return Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(sb.toString()).build())
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
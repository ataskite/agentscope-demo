package com.skloda.agentscope.runtime;

import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.*;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MsgHub runtime — multi-round expert roundtable discussion with moderator synthesis.
 * Each expert speaks in sequence across multiple rounds, seeing all previous messages.
 * The moderator summarizes the discussion at the end.
 */
public class MsgHubRuntime implements StreamingAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(MsgHubRuntime.class);

    @Getter
    private final ObservabilityHook hook;
    private final List<ReActAgent> experts;
    private final ReActAgent moderator;
    private final String pipelineId;
    private final int rounds;

    public MsgHubRuntime(List<ReActAgent> experts, ReActAgent moderator,
                         ObservabilityHook hook, String pipelineId, int rounds) {
        this.experts = experts;
        this.moderator = moderator;
        this.hook = hook;
        this.pipelineId = pipelineId;
        this.rounds = rounds;
    }

    @Override
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        Flux<Map<String, Object>> sinkEvents = hook.getEventSink().asFlux();

        Flux<Map<String, Object>> roundtableFlux = Flux.create(fluxSink -> {
            List<String> participantNames = new ArrayList<>();
            experts.forEach(e -> participantNames.add(e.getName()));
            participantNames.add(moderator.getName());
            hook.emitRoundtableStart(pipelineId, participantNames, rounds);

            String topic = extractText(userMsg);
            List<String> discussionLog = new ArrayList<>();

            Mono<Void> chain = Mono.empty();

            for (int round = 1; round <= rounds; round++) {
                final int roundNum = round;
                chain = chain.then(Mono.defer(() -> {
                    hook.emitRoundStart(roundNum);
                    Mono<Void> roundChain = Mono.empty();

                    for (ReActAgent expert : experts) {
                        final String expertName = expert.getName();
                        roundChain = roundChain.then(Mono.defer(() -> {
                            String prompt = buildExpertPrompt(topic, discussionLog, roundNum, expertName);
                            Msg expertMsg = Msg.builder()
                                    .name("user").role(MsgRole.USER)
                                    .textContent(prompt).build();

                            return expert.call(expertMsg).doOnNext(response -> {
                                String content = extractText(response);
                                discussionLog.add("【" + expertName + "】(第" + roundNum + "轮): " + content);
                                hook.emitRoundMessage(expertName, content);
                                fluxSink.next(Map.of("type", "round_message",
                                        "round", roundNum, "agent", expertName, "content", content));
                            }).then();
                        }));
                    }

                    return roundChain.doOnTerminate(() -> hook.emitRoundEnd(roundNum));
                }));
            }

            // Moderator synthesis after all rounds complete
            chain.then(Mono.defer(() -> {
                String moderatorPrompt = buildModeratorPrompt(topic, discussionLog);
                Msg moderatorMsg = Msg.builder()
                        .name("user").role(MsgRole.USER)
                        .textContent(moderatorPrompt).build();

                return moderator.call(moderatorMsg).doOnNext(response -> {
                    String synthesis = extractText(response);
                    hook.emitRoundtableSummary(moderator.getName(), synthesis);
                    fluxSink.next(Map.of("type", "roundtable_summary",
                            "agent", moderator.getName(), "content", synthesis));
                    fluxSink.next(Map.of("type", "text", "content", synthesis));
                    fluxSink.next(Map.of("type", "done"));
                    fluxSink.complete();
                }).then();
            })).subscribe(
                    v -> {},
                    error -> {
                        log.error("MsgHub roundtable error", error);
                        fluxSink.next(Map.of("type", "error", "message", error.getMessage()));
                        fluxSink.next(Map.of("type", "done"));
                        fluxSink.complete();
                    }
            );
        });

        return Flux.merge(sinkEvents, roundtableFlux).doOnCancel(this::close);
    }

    private String buildExpertPrompt(String topic, List<String> discussionLog, int round, String expertName) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 讨论主题\n").append(topic).append("\n\n");
        if (!discussionLog.isEmpty()) {
            sb.append("## 之前的讨论记录\n");
            for (String entry : discussionLog) {
                sb.append("- ").append(entry).append("\n");
            }
            sb.append("\n");
        }
        sb.append("## 你的任务\n");
        sb.append("你是第 ").append(round).append(" 轮的讨论参与者 (").append(expertName).append(")。\n");
        sb.append("请就上述主题发表你的专业观点和见解。\n");
        return sb.toString();
    }

    private String buildModeratorPrompt(String topic, List<String> discussionLog) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 讨论主题\n").append(topic).append("\n\n");
        sb.append("## 完整讨论记录\n");
        for (String entry : discussionLog) {
            sb.append("- ").append(entry).append("\n");
        }
        sb.append("\n## 你的任务\n");
        sb.append("你是讨论主持人。请综合所有参与者的观点，给出全面、客观的总结。\n");
        return sb.toString();
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

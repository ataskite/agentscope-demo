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
 * Debate runtime — multi-expert debate with judge synthesis.
 * All agents except the last are experts; the last agent is the judge.
 *
 * <p>Each sub-agent (experts and judge) runs via {@code streamEvents()} (bridged by
 * {@link MultiAgentStreamSupport}), so the frontend receives every thinking/tool/text delta
 * from every participant, not just a single final text per turn.
 */
public class DebateRuntime implements StreamingAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(DebateRuntime.class);

    @Getter
    private final ObservabilityHook hook;
    private final List<ReActAgent> experts;
    private final ReActAgent judge;
    private final String pipelineId;
    private final int rounds;

    public DebateRuntime(List<ReActAgent> experts, ReActAgent judge,
                         ObservabilityHook hook, String pipelineId, int rounds) {
        this.experts = experts;
        this.judge = judge;
        this.hook = hook;
        this.pipelineId = pipelineId;
        this.rounds = rounds;
    }

    @Override
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        Flux<Map<String, Object>> sinkEvents = hook.getEventSink().asFlux();

        Flux<Map<String, Object>> debateFlux = Flux.create(fluxSink -> {
            List<String> allAgents = new ArrayList<>();
            experts.forEach(e -> allAgents.add(e.getName()));
            allAgents.add(judge.getName());
            hook.emitPipelineStart(pipelineId, allAgents);

            String topic = MultiAgentStreamSupport.extractText(userMsg);
            List<String> allArguments = new ArrayList<>();
            Mono<Void> chain = Mono.empty();

            for (int round = 0; round < rounds; round++) {
                final int roundNum = round;
                for (ReActAgent expert : experts) {
                    final String expertName = expert.getName();
                    chain = chain.then(Mono.defer(() -> {
                        String debateContext = buildDebateContext(topic, allArguments, roundNum, expertName);
                        Msg debateMsg = Msg.builder()
                                .name("user").role(MsgRole.USER)
                                .textContent(debateContext).build();

                        return MultiAgentStreamSupport.runSubAgent(expert, debateMsg, expertName, fluxSink, null)
                                .doOnNext(argument -> {
                                    allArguments.add(expertName + ": " + argument);
                                    fluxSink.next(Map.of("type", "round_message",
                                            "round", roundNum, "agent", expertName, "content", argument));
                                })
                                .then();
                    }));
                }
            }

            chain.then(Mono.defer(() -> {
                String judgeInput = buildJudgePrompt(topic, allArguments);
                Msg judgeMsg = Msg.builder()
                        .name("user").role(MsgRole.USER)
                        .textContent(judgeInput).build();
                return MultiAgentStreamSupport.runSubAgent(judge, judgeMsg, judge.getName(), fluxSink, null)
                        .doOnNext(synthesis -> {
                            fluxSink.next(Map.of("type", "roundtable_summary",
                                    "agent", judge.getName(), "content", synthesis));
                            fluxSink.next(Map.of("type", "text", "content", synthesis));
                            hook.emitPipelineEnd(pipelineId, experts.size() * rounds + 1, 0);
                            fluxSink.next(Map.of("type", "done"));
                            fluxSink.complete();
                        })
                        .then();
            })).subscribe(
                    v -> {},
                    error -> {
                        log.error("Debate pipeline error", error);
                        fluxSink.next(Map.of("type", "error", "message", error.getMessage()));
                        fluxSink.next(Map.of("type", "done"));
                        fluxSink.complete();
                    }
            );
        });

        return Flux.merge(sinkEvents, debateFlux.doFinally(s -> close()))
                .doOnCancel(this::close);
    }

    private String buildDebateContext(String topic, List<String> previousArgs, int round, String expertName) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 辩论主题\n").append(topic).append("\n\n");
        if (!previousArgs.isEmpty()) {
            sb.append("## 之前的辩论发言\n");
            for (String arg : previousArgs) {
                sb.append("- ").append(arg).append("\n");
            }
            sb.append("\n");
        }
        sb.append("## 你的任务\n");
        sb.append("你是第 ").append(round + 1).append(" 轮的辩论者 (").append(expertName).append(")。\n");
        sb.append("请就上述主题发表你的观点和论据。\n");
        return sb.toString();
    }

    private String buildJudgePrompt(String topic, List<String> allArguments) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 辩论主题\n").append(topic).append("\n\n");
        sb.append("## 所有辩论发言\n");
        for (String arg : allArguments) {
            sb.append("- ").append(arg).append("\n");
        }
        sb.append("\n## 你的任务\n");
        sb.append("你是裁判。请综合所有辩论者的观点，给出客观的总结和评判。\n");
        return sb.toString();
    }

    @Override
    public void close() {
        hook.getEventSink().complete();
    }
}

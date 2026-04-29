package com.skloda.agentscope.composite;

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.pipeline.Pipeline;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public class DebatePipeline implements Pipeline<Msg> {

    private final List<AgentBase> debaters;
    private final AgentBase judge;

    public DebatePipeline(List<AgentBase> debaters, AgentBase judge) {
        if (debaters == null || debaters.size() < 2) {
            throw new IllegalArgumentException("DEBATE requires at least 2 debaters + 1 judge");
        }
        if (judge == null) {
            throw new IllegalArgumentException("DEBATE requires a judge agent");
        }
        this.debaters = debaters;
        this.judge = judge;
    }

    @Override
    public Mono<Msg> execute(Msg input) {
        return execute(input, null);
    }

    @Override
    public Mono<Msg> execute(Msg input, Class<?> structuredOutputClass) {
        List<Mono<Msg>> debaterMonos = debaters.stream()
                .map(d -> d.call(input))
                .toList();

        return Flux.merge(debaterMonos)
                .collectList()
                .flatMap(viewpoints -> {
                    Msg judgeInput = buildJudgeInput(input, viewpoints);
                    return judge.call(judgeInput);
                });
    }

    Msg buildJudgeInput(Msg originalQuestion, List<Msg> viewpoints) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 待评估的提案\n\n");

        if (originalQuestion != null && originalQuestion.getContent() != null) {
            for (ContentBlock block : originalQuestion.getContent()) {
                if (block instanceof TextBlock tb) {
                    sb.append(tb.getText()).append("\n\n");
                }
            }
        }

        sb.append("## 各位专家的观点\n\n");
        for (int i = 0; i < viewpoints.size(); i++) {
            sb.append("### 专家 ").append(i + 1).append(" 的观点\n\n");
            Msg vp = viewpoints.get(i);
            if (vp != null && vp.getContent() != null) {
                for (ContentBlock block : vp.getContent()) {
                    if (block instanceof TextBlock tb) {
                        sb.append(tb.getText()).append("\n\n");
                    }
                }
            }
        }

        sb.append("---\n\n请综合以上所有专家的观点，给出你的最终裁决。");

        return Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(sb.toString()).build())
                .build();
    }
}

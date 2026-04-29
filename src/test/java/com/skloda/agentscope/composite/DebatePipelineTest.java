package com.skloda.agentscope.composite;

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.interruption.InterruptContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DebatePipelineTest {

    private Msg textMsg(String text) {
        return Msg.builder()
                .content(TextBlock.builder().text(text).build())
                .build();
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : msg.getContent()) {
            if (b instanceof TextBlock tb) sb.append(tb.getText());
        }
        return sb.toString();
    }

    @Test
    void constructorRequiresAtLeastTwoDebaters() {
        AgentBase debater = new FakeAgent("debater");
        AgentBase judge = new FakeAgent("judge");
        assertThrows(IllegalArgumentException.class,
                () -> new DebatePipeline(List.of(debater), judge));
    }

    @Test
    void constructorRequiresNonNullDebaters() {
        AgentBase judge = new FakeAgent("judge");
        assertThrows(IllegalArgumentException.class,
                () -> new DebatePipeline(null, judge));
    }

    @Test
    void constructorRequiresNonNullJudge() {
        AgentBase d1 = new FakeAgent("d1");
        AgentBase d2 = new FakeAgent("d2");
        assertThrows(IllegalArgumentException.class,
                () -> new DebatePipeline(List.of(d1, d2), null));
    }

    @Test
    void buildJudgeInputAggregatesAllViewpoints() {
        AgentBase d1 = new FakeAgent("d1");
        AgentBase d2 = new FakeAgent("d2");
        AgentBase judge = new FakeAgent("judge");
        DebatePipeline pipeline = new DebatePipeline(List.of(d1, d2), judge);

        Msg question = textMsg("Should we adopt remote work?");
        List<Msg> viewpoints = List.of(
                textMsg("Remote work boosts productivity"),
                textMsg("Remote work hurts team cohesion")
        );

        Msg result = pipeline.buildJudgeInput(question, viewpoints);
        String text = extractText(result);

        assertTrue(text.contains("待评估的提案"));
        assertTrue(text.contains("Should we adopt remote work?"));
        assertTrue(text.contains("专家 1"));
        assertTrue(text.contains("Remote work boosts productivity"));
        assertTrue(text.contains("专家 2"));
        assertTrue(text.contains("Remote work hurts team cohesion"));
        assertTrue(text.contains("最终裁决"));
    }

    @Test
    void buildJudgeInputHandlesNullQuestionContent() {
        AgentBase d1 = new FakeAgent("d1");
        AgentBase d2 = new FakeAgent("d2");
        AgentBase judge = new FakeAgent("judge");
        DebatePipeline pipeline = new DebatePipeline(List.of(d1, d2), judge);

        Msg question = Msg.builder().build();
        List<Msg> viewpoints = List.of(textMsg("viewpoint"));

        Msg result = pipeline.buildJudgeInput(question, viewpoints);
        String text = extractText(result);

        assertTrue(text.contains("待评估的提案"));
        assertTrue(text.contains("最终裁决"));
    }

    static class FakeAgent extends AgentBase {
        private final String responseText;

        FakeAgent(String name) {
            super(name);
            this.responseText = name;
        }

        @Override
        protected reactor.core.publisher.Mono<Msg> doCall(java.util.List<Msg> messages) {
            return reactor.core.publisher.Mono.just(textMsg(responseText));
        }

        @Override
        protected reactor.core.publisher.Mono<Msg> handleInterrupt(InterruptContext context, Msg... messages) {
            return reactor.core.publisher.Mono.empty();
        }

        private static Msg textMsg(String text) {
            return Msg.builder()
                    .content(TextBlock.builder().text(text).build())
                    .build();
        }
    }
}

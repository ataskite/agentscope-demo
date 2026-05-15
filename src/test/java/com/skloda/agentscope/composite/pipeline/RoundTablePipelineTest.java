package com.skloda.agentscope.composite.pipeline;

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.interruption.InterruptContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoundTablePipelineTest {

    private List<Map<String, Object>> capturedEvents;

    @BeforeEach
    void setUp() {
        capturedEvents = new ArrayList<>();
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
    void executeSingleRoundTwoExperts() {
        RoundTablePipeline pipeline = new RoundTablePipeline(
                new FakeAgent("moderator", "summary"),
                List.of(new FakeAgent("architect", "use microservices"), new FakeAgent("dba", "use postgresql")),
                1);
        pipeline.addEventConsumer((type, data) -> capturedEvents.add(Map.of("type", type, "data", data)));

        StepVerifier.create(pipeline.execute(textMsg("design a system")))
                .assertNext(msg -> assertTrue(extractText(msg).contains("summary")))
                .verifyComplete();

        assertTrue(capturedEvents.stream().anyMatch(e -> "roundtable_start".equals(e.get("type"))));
        assertTrue(capturedEvents.stream().anyMatch(e -> "round_start".equals(e.get("type"))));
        assertTrue(capturedEvents.stream().anyMatch(e -> "round_message".equals(e.get("type"))));
        assertTrue(capturedEvents.stream().anyMatch(e -> "round_end".equals(e.get("type"))));
        assertTrue(capturedEvents.stream().anyMatch(e -> "roundtable_summary".equals(e.get("type"))));
    }

    @Test
    void executeMultipleRounds() {
        RoundTablePipeline pipeline = new RoundTablePipeline(
                new FakeAgent("mod", "final"), List.of(new FakeAgent("expert", "opinion")), 2);
        pipeline.addEventConsumer((type, data) -> capturedEvents.add(Map.of("type", type, "data", data)));

        StepVerifier.create(pipeline.execute(textMsg("topic")))
                .assertNext(msg -> assertNotNull(msg))
                .verifyComplete();

        assertEquals(2, capturedEvents.stream().filter(e -> "round_start".equals(e.get("type"))).count());
    }

    @Test
    void executeSingleExpert() {
        RoundTablePipeline pipeline = new RoundTablePipeline(
                new FakeAgent("mod", "conclusion"), List.of(new FakeAgent("expert", "view")), 1);
        StepVerifier.create(pipeline.execute(textMsg("discuss")))
                .assertNext(msg -> assertNotNull(msg))
                .verifyComplete();
    }

    @Test
    void roundMessageEventsCaptured() {
        RoundTablePipeline pipeline = new RoundTablePipeline(
                new FakeAgent("mod", "summary"), List.of(new FakeAgent("security-expert", "auth needed")), 1);
        pipeline.addEventConsumer((type, data) -> capturedEvents.add(Map.of("type", type, "data", data)));

        StepVerifier.create(pipeline.execute(textMsg("security review")))
                .assertNext(msg -> assertNotNull(msg))
                .verifyComplete();

        assertFalse(capturedEvents.stream().filter(e -> "round_message".equals(e.get("type"))).toList().isEmpty());
    }

    private Msg textMsg(String text) {
        return Msg.builder().content(TextBlock.builder().text(text).build()).build();
    }

    static class FakeAgent extends AgentBase {
        private final String responseText;
        FakeAgent(String name, String responseText) { super(name); this.responseText = responseText; }
        @Override
        protected Mono<Msg> doCall(List<Msg> messages) {
            return Mono.just(Msg.builder().content(TextBlock.builder().text(responseText).build()).build());
        }
        @Override
        protected Mono<Msg> handleInterrupt(InterruptContext context, Msg... messages) { return Mono.empty(); }
    }
}

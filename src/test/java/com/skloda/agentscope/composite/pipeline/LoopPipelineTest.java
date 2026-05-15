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

class LoopPipelineTest {

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
    void executeWithAutoExitOnApproval() {
        FakeAgent writer = new FakeAgent("writer", "draft content");
        FakeAgent critic = new FakeAgent("critic", "内容很好，通过");

        LoopPipeline pipeline = new LoopPipeline(writer, critic, 3, true);
        pipeline.addEventConsumer((type, data) -> capturedEvents.add(Map.of("type", type, "data", data)));

        StepVerifier.create(pipeline.execute(textMsg("write a report")))
                .assertNext(msg -> {
                    assertTrue(extractText(msg).contains("draft content"));
                    assertTrue(extractText(msg).contains("评审总结"));
                })
                .verifyComplete();

        assertTrue(capturedEvents.stream().anyMatch(e -> "loop_start".equals(e.get("type"))));
        assertTrue(capturedEvents.stream().anyMatch(e -> "loop_end".equals(e.get("type"))));
    }

    @Test
    void executeHitsMaxIterationsWithoutApproval() {
        FakeAgent writer = new FakeAgent("writer", "draft");
        FakeAgent critic = new FakeAgent("critic", "需要修改");

        LoopPipeline pipeline = new LoopPipeline(writer, critic, 2, true);
        pipeline.addEventConsumer((type, data) -> capturedEvents.add(Map.of("type", type, "data", data)));

        StepVerifier.create(pipeline.execute(textMsg("write")))
                .assertNext(msg -> assertNotNull(msg))
                .verifyComplete();

        assertEquals(2, capturedEvents.stream().filter(e -> "loop_start".equals(e.get("type"))).count());
    }

    @Test
    void executeWithoutAutoExit() {
        LoopPipeline pipeline = new LoopPipeline(
                new FakeAgent("writer", "content"), new FakeAgent("critic", "ok"), 1, false);
        StepVerifier.create(pipeline.execute(textMsg("task")))
                .assertNext(msg -> assertNotNull(msg))
                .verifyComplete();
    }

    @Test
    void approvalDetectedForEnglishAPPROVED() {
        LoopPipeline pipeline = new LoopPipeline(
                new FakeAgent("w", "text"), new FakeAgent("c", "APPROVED"), 5, true);
        StepVerifier.create(pipeline.execute(textMsg("t")))
                .assertNext(msg -> assertNotNull(msg))
                .verifyComplete();
    }

    @Test
    void approvalDetectedForLowercaseApproved() {
        LoopPipeline pipeline = new LoopPipeline(
                new FakeAgent("w", "text"), new FakeAgent("c", "this is approved"), 5, true);
        StepVerifier.create(pipeline.execute(textMsg("t")))
                .assertNext(msg -> assertNotNull(msg))
                .verifyComplete();
    }

    @Test
    void eventConsumerReceivesAllEventTypes() {
        LoopPipeline pipeline = new LoopPipeline(
                new FakeAgent("w", "text"), new FakeAgent("c", "通过"), 5, true);
        pipeline.addEventConsumer((type, data) -> capturedEvents.add(Map.of("type", type, "data", data)));

        StepVerifier.create(pipeline.execute(textMsg("do")))
                .assertNext(msg -> assertNotNull(msg))
                .verifyComplete();

        List<String> types = capturedEvents.stream().map(e -> (String) e.get("type")).toList();
        assertTrue(types.contains("loop_start"));
        assertTrue(types.contains("pipeline_step_end"));
        assertTrue(types.contains("loop_iteration_result"));
        assertTrue(types.contains("loop_end"));
    }

    @Test
    void threeIterationsAllTracked() {
        LoopPipeline pipeline = new LoopPipeline(
                new FakeAgent("w", "draft"), new FakeAgent("c", "redo"), 3, true);
        pipeline.addEventConsumer((type, data) -> capturedEvents.add(Map.of("type", type, "data", data)));

        StepVerifier.create(pipeline.execute(textMsg("task")))
                .assertNext(msg -> assertNotNull(msg))
                .verifyComplete();

        assertEquals(3, capturedEvents.stream().filter(e -> "loop_iteration_result".equals(e.get("type"))).count());
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

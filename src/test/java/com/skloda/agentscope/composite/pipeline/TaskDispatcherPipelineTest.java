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

class TaskDispatcherPipelineTest {

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
    void executeParallelAggregatesResults() {
        TaskDispatcherPipeline pipeline = new TaskDispatcherPipeline(
                List.of(new FakeAgent("researcher", "research"), new FakeAgent("designer", "design")),
                List.of("Research: {input}", "Design: {input}"),
                List.of("researcher", "designer"));
        pipeline.addEventConsumer((type, data) -> capturedEvents.add(Map.of("type", type, "data", data)));

        StepVerifier.create(pipeline.execute(textMsg("project")))
                .assertNext(msg -> {
                    String text = extractText(msg);
                    assertTrue(text.contains("researcher"));
                    assertTrue(text.contains("designer"));
                    assertTrue(text.contains("的结果"));
                })
                .verifyComplete();

        assertTrue(capturedEvents.stream().anyMatch(e -> "task_delegate".equals(e.get("type"))));
        assertTrue(capturedEvents.stream().anyMatch(e -> "task_aggregate".equals(e.get("type"))));
    }

    @Test
    void executeSingleAgent() {
        TaskDispatcherPipeline pipeline = new TaskDispatcherPipeline(
                List.of(new FakeAgent("solo", "result")), List.of("{input}"), List.of("solo"));
        StepVerifier.create(pipeline.execute(textMsg("task")))
                .assertNext(msg -> assertTrue(extractText(msg).contains("result")))
                .verifyComplete();
    }

    @Test
    void executeWithDefaultIds() {
        TaskDispatcherPipeline pipeline = new TaskDispatcherPipeline(
                List.of(new FakeAgent("a", "r")), List.of(), List.of());
        StepVerifier.create(pipeline.execute(textMsg("input")))
                .assertNext(msg -> assertTrue(extractText(msg).contains("agent-0")))
                .verifyComplete();
    }

    @Test
    void threeAgentsAllResults() {
        TaskDispatcherPipeline pipeline = new TaskDispatcherPipeline(
                List.of(new FakeAgent("a1", "r1"), new FakeAgent("a2", "r2"), new FakeAgent("a3", "r3")),
                List.of("{input}", "{input}", "{input}"),
                List.of("a1", "a2", "a3"));
        StepVerifier.create(pipeline.execute(textMsg("task")))
                .assertNext(msg -> {
                    String text = extractText(msg);
                    assertTrue(text.contains("r1"));
                    assertTrue(text.contains("r2"));
                    assertTrue(text.contains("r3"));
                })
                .verifyComplete();
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

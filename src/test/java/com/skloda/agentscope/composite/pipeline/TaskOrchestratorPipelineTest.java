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

class TaskOrchestratorPipelineTest {

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
    void executeSequentialChains() {
        TaskOrchestratorPipeline pipeline = new TaskOrchestratorPipeline(
                List.of(new FakeAgent("researcher", "data"), new FakeAgent("analyst", "analysis")),
                List.of("Research: {input}", "Analyze: {prevOutput}"),
                List.of("researcher", "analyst"));
        pipeline.addEventConsumer((type, data) -> capturedEvents.add(Map.of("type", type, "data", data)));

        StepVerifier.create(pipeline.execute(textMsg("AI trends")))
                .assertNext(msg -> assertTrue(extractText(msg).contains("analysis")))
                .verifyComplete();

        assertTrue(capturedEvents.stream().anyMatch(e -> "task_delegate".equals(e.get("type"))));
        assertTrue(capturedEvents.stream().anyMatch(e -> "task_start".equals(e.get("type"))));
        assertTrue(capturedEvents.stream().anyMatch(e -> "task_end".equals(e.get("type"))));
    }

    @Test
    void executeSingleAgent() {
        TaskOrchestratorPipeline pipeline = new TaskOrchestratorPipeline(
                List.of(new FakeAgent("solo", "result")), List.of("{input}"), List.of("solo"));
        StepVerifier.create(pipeline.execute(textMsg("task")))
                .assertNext(msg -> assertTrue(extractText(msg).contains("result")))
                .verifyComplete();
    }

    @Test
    void executeWithDefaultTemplate() {
        TaskOrchestratorPipeline pipeline = new TaskOrchestratorPipeline(
                List.of(new FakeAgent("a", "ok")), List.of(), List.of());
        StepVerifier.create(pipeline.execute(textMsg("input")))
                .assertNext(msg -> assertNotNull(msg))
                .verifyComplete();
    }

    @Test
    void executeEmptyAgentsReturnsNoOutput() {
        TaskOrchestratorPipeline pipeline = new TaskOrchestratorPipeline(List.of(), List.of(), List.of());
        StepVerifier.create(pipeline.execute(textMsg("test")))
                .assertNext(msg -> assertTrue(extractText(msg).contains("No output")))
                .verifyComplete();
    }

    @Test
    void templatePrevOutputReplacement() {
        List<String> receivedTasks = new ArrayList<>();
        FakeAgent a1 = new FakeAgent("a1", "output1") {
            @Override
            protected Mono<Msg> doCall(List<Msg> messages) {
                StringBuilder sb = new StringBuilder();
                for (ContentBlock b : messages.get(messages.size() - 1).getContent()) {
                    if (b instanceof TextBlock tb) sb.append(tb.getText());
                }
                receivedTasks.add(sb.toString());
                return Mono.just(Msg.builder().content(TextBlock.builder().text("output1").build()).build());
            }
        };
        FakeAgent a2 = new FakeAgent("a2", "output2") {
            @Override
            protected Mono<Msg> doCall(List<Msg> messages) {
                StringBuilder sb = new StringBuilder();
                for (ContentBlock b : messages.get(messages.size() - 1).getContent()) {
                    if (b instanceof TextBlock tb) sb.append(tb.getText());
                }
                receivedTasks.add(sb.toString());
                return Mono.just(Msg.builder().content(TextBlock.builder().text("output2").build()).build());
            }
        };

        TaskOrchestratorPipeline pipeline = new TaskOrchestratorPipeline(
                List.of(a1, a2), List.of("{input}", "Prev: {prevOutput}"), List.of("a1", "a2"));
        StepVerifier.create(pipeline.execute(textMsg("original")))
                .assertNext(msg -> assertNotNull(msg))
                .verifyComplete();

        assertEquals(2, receivedTasks.size());
        assertTrue(receivedTasks.get(0).contains("original"));
        assertTrue(receivedTasks.get(1).contains("output1"));
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

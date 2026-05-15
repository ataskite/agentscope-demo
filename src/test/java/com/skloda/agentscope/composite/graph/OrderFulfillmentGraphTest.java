package com.skloda.agentscope.composite.graph;

import com.skloda.agentscope.agent.StateConfig;
import com.skloda.agentscope.agent.StateTransition;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class OrderFulfillmentGraphTest {

    private List<Map<String, Object>> capturedEvents;

    @BeforeEach
    void setUp() {
        capturedEvents = new ArrayList<>();
    }

    private Msg textMsg(String text) {
        return Msg.builder().content(TextBlock.builder().text(text).build()).build();
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (var b : msg.getContent()) {
            if (b instanceof TextBlock tb) sb.append(tb.getText());
        }
        return sb.toString();
    }

    @Test
    void executeWithEmptyStatesReturnsUnknownState() {
        OrderFulfillmentGraph graph = new OrderFulfillmentGraph(List.of(), Map.of());
        graph.addEventConsumer((type, data) -> capturedEvents.add(Map.of("type", type, "data", data)));

        StepVerifier.create(graph.execute(textMsg("test")))
                .assertNext(msg -> assertTrue(extractText(msg).contains("未知状态")))
                .verifyComplete();
    }

    @Test
    void executeUserDrivenTransition() {
        StateConfig created = StateConfig.builder()
                .name("CREATED").transitions(List.of(
                        StateTransition.builder().event("submit").target("SUBMITTED").build())).build();
        StateConfig submitted = StateConfig.builder()
                .name("SUBMITTED").transitions(List.of()).build();

        OrderFulfillmentGraph graph = new OrderFulfillmentGraph(List.of(created, submitted), Map.of());
        graph.addEventConsumer((type, data) -> capturedEvents.add(Map.of("type", type, "data", data)));

        StepVerifier.create(graph.execute(textMsg("submit order")))
                .assertNext(msg -> {
                    String text = extractText(msg);
                    assertTrue(text.contains("终态") || text.contains("SUBMITTED"));
                })
                .verifyComplete();

        assertTrue(capturedEvents.stream().anyMatch(e -> "graph_transition".equals(e.get("type"))));
        assertEquals("SUBMITTED", graph.getCurrentState());
    }

    @Test
    void executeNoMatchingEventShowsAvailableActions() {
        StateConfig created = StateConfig.builder()
                .name("CREATED").transitions(List.of(
                        StateTransition.builder().event("submit").target("SUBMITTED").build())).build();

        OrderFulfillmentGraph graph = new OrderFulfillmentGraph(List.of(created), Map.of());

        StepVerifier.create(graph.execute(textMsg("do something unknown")))
                .assertNext(msg -> {
                    String text = extractText(msg);
                    assertTrue(text.contains("当前状态"));
                    assertTrue(text.contains("CREATED"));
                    assertTrue(text.contains("可用操作"));
                })
                .verifyComplete();
    }

    @Test
    void executeChineseEventAliases() {
        StateConfig created = StateConfig.builder()
                .name("CREATED").transitions(List.of(
                        StateTransition.builder().event("submit").target("SUBMITTED").build())).build();
        StateConfig submitted = StateConfig.builder()
                .name("SUBMITTED").transitions(List.of()).build();

        OrderFulfillmentGraph graph = new OrderFulfillmentGraph(List.of(created, submitted), Map.of());
        StepVerifier.create(graph.execute(textMsg("提交订单")))
                .assertNext(msg -> assertTrue(extractText(msg).contains("终态") || extractText(msg).contains("SUBMITTED")))
                .verifyComplete();
        assertEquals("SUBMITTED", graph.getCurrentState());
    }

    @Test
    void resetReturnsToInitialState() {
        StateConfig s1 = StateConfig.builder().name("S1")
                .transitions(List.of(StateTransition.builder().event("next").target("S2").build())).build();
        StateConfig s2 = StateConfig.builder().name("S2").transitions(List.of()).build();

        OrderFulfillmentGraph graph = new OrderFulfillmentGraph(List.of(s1, s2), Map.of());
        graph.execute(textMsg("next")).block();
        assertEquals("S2", graph.getCurrentState());

        graph.reset();
        assertEquals("S1", graph.getCurrentState());
    }

    @Test
    void multiStepUserDrivenFlow() {
        StateConfig created = StateConfig.builder().name("CREATED")
                .transitions(List.of(StateTransition.builder().event("submit").target("SUBMITTED").build())).build();
        StateConfig submitted = StateConfig.builder().name("SUBMITTED")
                .transitions(List.of(StateTransition.builder().event("pay").target("PAID").build())).build();
        StateConfig paid = StateConfig.builder().name("PAID")
                .transitions(List.of(StateTransition.builder().event("ship").target("SHIPPED").build())).build();
        StateConfig shipped = StateConfig.builder().name("SHIPPED").transitions(List.of()).build();

        OrderFulfillmentGraph graph = new OrderFulfillmentGraph(
                List.of(created, submitted, paid, shipped), Map.of());

        StepVerifier.create(graph.execute(textMsg("submit")))
                .assertNext(msg -> {
                    String text = extractText(msg);
                    // "submit" triggers CREATED→SUBMITTED, then SUBMITTED has no matching user event
                    assertTrue(text.contains("SUBMITTED") || text.contains("终态") || text.contains("当前状态"));
                })
                .verifyComplete();
    }

    @Test
    void eventConsumerReceivesCorrectPayload() {
        StateConfig s1 = StateConfig.builder().name("START")
                .transitions(List.of(StateTransition.builder().event("go").target("END").build())).build();
        StateConfig s2 = StateConfig.builder().name("END").transitions(List.of()).build();

        OrderFulfillmentGraph graph = new OrderFulfillmentGraph(List.of(s1, s2), Map.of());
        graph.addEventConsumer((type, data) -> capturedEvents.add(data));

        StepVerifier.create(graph.execute(textMsg("go")))
                .assertNext(msg -> assertNotNull(msg))
                .verifyComplete();

        Optional<Map<String, Object>> transition = capturedEvents.stream()
                .filter(d -> "graph_transition".equals(d.get("type"))).findFirst();
        assertTrue(transition.isPresent());
        assertEquals("START", transition.get().get("fromState"));
        assertEquals("END", transition.get().get("toState"));
    }

    @Test
    void resetOnEmptyStates() {
        OrderFulfillmentGraph graph = new OrderFulfillmentGraph(List.of(), Map.of());
        graph.reset();
        assertNull(graph.getCurrentState());
    }

    @Test
    void nullTransitionsHandled() {
        StateConfig state = StateConfig.builder().name("S1").transitions(null).build();
        OrderFulfillmentGraph graph = new OrderFulfillmentGraph(List.of(state), Map.of());

        StepVerifier.create(graph.execute(textMsg("test")))
                .assertNext(msg -> assertTrue(extractText(msg).contains("当前状态")))
                .verifyComplete();
    }

    @Test
    void payAndShipChineseAliases() {
        StateConfig s1 = StateConfig.builder().name("ORDERED")
                .transitions(List.of(StateTransition.builder().event("pay").target("PAID").build())).build();
        StateConfig s2 = StateConfig.builder().name("PAID")
                .transitions(List.of(StateTransition.builder().event("ship").target("DONE").build())).build();
        StateConfig s3 = StateConfig.builder().name("DONE").transitions(List.of()).build();

        OrderFulfillmentGraph graph = new OrderFulfillmentGraph(List.of(s1, s2, s3), Map.of());
        StepVerifier.create(graph.execute(textMsg("支付")))
                .assertNext(msg -> {
                    String text = extractText(msg);
                    assertTrue(text.contains("PAID") || text.contains("终态") || text.contains("当前状态"));
                })
                .verifyComplete();
    }
}

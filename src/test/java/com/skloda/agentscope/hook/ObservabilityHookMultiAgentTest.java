package com.skloda.agentscope.hook;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.memory.autocontext.CompressionEvent;
import io.agentscope.core.model.DashScopeChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ObservabilityHookMultiAgentTest {

    private ObservabilityHook hook;
    private List<Map<String, Object>> capturedEvents;

    @BeforeEach
    void setUp() {
        hook = new ObservabilityHook();
        capturedEvents = new ArrayList<>();
        hook.addConsumer((type, data) -> capturedEvents.add(Map.of("type", type, "data", data)));
    }

    @Test
    void testEmitPipelineStart() {
        hook.emitPipelineStart("test-pipeline", List.of("agent1", "agent2"));
        assertEquals(1, capturedEvents.size());
        assertEquals("pipeline_start", capturedEvents.get(0).get("type"));
    }

    @Test
    void testEmitRoutingDecision() {
        hook.emitRoutingDecision("test-router", "agent1", "User asked about documents");
        assertEquals(1, capturedEvents.size());
        assertEquals("routing_decision", capturedEvents.get(0).get("type"));
    }

    @Test
    void testEmitHandoffStart() {
        hook.emitHandoffStart("support-agent", "sales-agent", "User wants to purchase");
        assertEquals(1, capturedEvents.size());
        assertEquals("handoff_start", capturedEvents.get(0).get("type"));
    }

    @Test
    void testPreReasoningEmitsNewAutoContextCompressionEvents() {
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey("test-api-key")
                .modelName("qwen-plus")
                .build();
        AutoContextMemory memory = new AutoContextMemory(AutoContextConfig.builder().build(), model);
        memory.getCompressionEvents().add(new CompressionEvent(
                CompressionEvent.LARGE_MESSAGE_OFFLOAD,
                123L,
                1,
                "prev",
                "next",
                "compressed",
                Map.of("tokenBefore", 100, "tokenAfter", 40)
        ));

        ReActAgent agent = ReActAgent.builder()
                .name("auto-context-agent")
                .model(model)
                .memory(memory)
                .build();

        hook.onEvent(new PreReasoningEvent(agent, "qwen-plus", null, List.of())).block();

        assertEquals(2, capturedEvents.size());
        assertEquals("memory_compression", capturedEvents.get(0).get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) capturedEvents.get(0).get("data");
        assertEquals(CompressionEvent.LARGE_MESSAGE_OFFLOAD, data.get("eventType"));
        assertEquals(1, data.get("compressedMessageCount"));
        assertEquals(100, data.get("tokenBefore"));
        assertEquals(40, data.get("tokenAfter"));
        assertEquals(60, data.get("tokenReduction"));
        assertEquals("llm_start", capturedEvents.get(1).get("type"));
    }
}

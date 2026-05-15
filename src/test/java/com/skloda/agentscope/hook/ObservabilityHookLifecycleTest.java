package com.skloda.agentscope.hook;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.*;
import io.agentscope.core.message.*;
import io.agentscope.core.model.DashScopeChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ObservabilityHookLifecycleTest {

    private ObservabilityHook hook;
    private List<Map<String, Object>> capturedEvents;
    private ReActAgent agent;

    @BeforeEach
    void setUp() {
        hook = new ObservabilityHook();
        capturedEvents = new ArrayList<>();
        hook.addConsumer((type, data) -> capturedEvents.add(Map.of("type", type, "data", data)));
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey("test-key").modelName("test-model").build();
        agent = ReActAgent.builder().name("test-agent").model(model).build();
    }

    @Test
    void preCallEmitsAgentStart() {
        hook.onEvent(new PreCallEvent(agent, List.of())).block();
        assertEquals(1, capturedEvents.size());
        assertEquals("agent_start", capturedEvents.get(0).get("type"));
    }

    @Test
    void preReasoningEmitsLlmStart() {
        hook.onEvent(new PreReasoningEvent(agent, "qwen-plus", null, List.of())).block();
        assertEquals(1, capturedEvents.size());
        assertEquals("llm_start", capturedEvents.get(0).get("type"));
    }

    @Test
    void reasoningChunkEmitsThinking() {
        Msg chunk = Msg.builder().content(ThinkingBlock.builder().thinking("analyzing...").build()).build();
        Msg accumulated = Msg.builder().content(TextBlock.builder().text("analyzing...").build()).build();
        hook.onEvent(new ReasoningChunkEvent(agent, "qwen-plus", null, chunk, accumulated)).block();
        assertEquals(1, capturedEvents.size());
        assertEquals("thinking", capturedEvents.get(0).get("type"));
    }

    @Test
    void reasoningChunkEmitsReasoningText() {
        Msg chunk = Msg.builder().content(TextBlock.builder().text("some reasoning").build()).build();
        Msg accumulated = Msg.builder().content(TextBlock.builder().text("some reasoning").build()).build();
        hook.onEvent(new ReasoningChunkEvent(agent, "qwen-plus", null, chunk, accumulated)).block();
        assertEquals(1, capturedEvents.size());
        assertEquals("reasoning_text", capturedEvents.get(0).get("type"));
    }

    @Test
    void reasoningChunkWithEmptyThinkingSkipsEmit() {
        Msg chunk = Msg.builder().content(ThinkingBlock.builder().thinking("").build()).build();
        Msg accumulated = Msg.builder().build();
        hook.onEvent(new ReasoningChunkEvent(agent, "qwen-plus", null, chunk, accumulated)).block();
        assertTrue(capturedEvents.isEmpty());
    }

    @Test
    void postReasoningEmitsLlmEnd() {
        Msg reasoningMsg = Msg.builder()
                .content(TextBlock.builder().text("result").build())
                .build();
        hook.onEvent(new PostReasoningEvent(agent, "qwen-plus", null, reasoningMsg)).block();
        assertEquals(1, capturedEvents.size());
        assertEquals("llm_end", capturedEvents.get(0).get("type"));
    }

    @Test
    void postReasoningExtractsToolCalls() {
        Msg reasoningMsg = Msg.builder()
                .content(TextBlock.builder().text("calling tool").build(),
                        ToolUseBlock.builder().id("t1").name("web_search").input(Map.of("query", "test")).build())
                .build();
        hook.onEvent(new PostReasoningEvent(agent, "qwen-plus", null, reasoningMsg)).block();
        assertEquals(1, capturedEvents.size());
        assertEquals("llm_end", capturedEvents.get(0).get("type"));
    }

    @Test
    void preActingEmitsToolStart() {
        ToolUseBlock toolUse = ToolUseBlock.builder().id("tool-1").name("web_search").input(Map.of("query", "weather")).build();
        hook.onEvent(new PreActingEvent(agent, null, toolUse)).block();
        assertEquals(1, capturedEvents.size());
        assertEquals("tool_start", capturedEvents.get(0).get("type"));
    }

    @Test
    void preActingIdentifiesSkillLoad() {
        ToolUseBlock toolUse = ToolUseBlock.builder().id("t1").name("load_skill_through_path")
                .input(Map.of("skillId", "docx_classpath-skills", "path", "SKILL.md")).build();
        hook.onEvent(new PreActingEvent(agent, null, toolUse)).block();
        assertEquals(1, capturedEvents.size());
        assertEquals("tool_start", capturedEvents.get(0).get("type"));
    }

    @Test
    void postActingEmitsToolEnd() {
        ToolUseBlock toolUse = ToolUseBlock.builder().id("t1").name("web_search").input(Map.of("query", "test")).build();
        ToolResultBlock result = ToolResultBlock.builder()
                .output(List.of(TextBlock.builder().text("found results").build())).build();
        hook.onEvent(new PostActingEvent(agent, null, toolUse, result)).block();
        assertEquals(1, capturedEvents.size());
        assertEquals("tool_end", capturedEvents.get(0).get("type"));
    }

    @Test
    void postActingDetectsRagRetrieval() {
        ToolUseBlock toolUse = ToolUseBlock.builder().id("t1").name("retrieve_knowledge")
                .input(Map.of("query", "test")).build();
        ToolResultBlock result = ToolResultBlock.builder()
                .output(List.of(TextBlock.builder().text("[1] doc with relevance score: 85").build())).build();
        hook.onEvent(new PostActingEvent(agent, null, toolUse, result)).block();
        assertEquals(1, capturedEvents.size());
        assertEquals("tool_end", capturedEvents.get(0).get("type"));
    }

    @Test
    void postCallEmitsAgentEnd() {
        Msg finalMsg = Msg.builder().content(TextBlock.builder().text("done").build()).build();
        hook.onEvent(new PostCallEvent(agent, finalMsg)).block();
        assertEquals(1, capturedEvents.size());
        assertEquals("agent_end", capturedEvents.get(0).get("type"));
    }

    @Test
    void errorEventEmitsError() {
        hook.onEvent(new ErrorEvent(agent, new RuntimeException("test error"))).block();
        assertEquals(1, capturedEvents.size());
        assertEquals("error", capturedEvents.get(0).get("type"));
    }

    @Test
    void resetClearsState() {
        hook.onEvent(new PreCallEvent(agent, List.of())).block();
        hook.reset();
        capturedEvents.clear();
        Msg finalMsg = Msg.builder().content(TextBlock.builder().text("done").build()).build();
        hook.onEvent(new PostCallEvent(agent, finalMsg)).block();
        assertEquals(1, capturedEvents.size());
    }

    @Test
    void removeConsumerStopsReceivingEvents() {
        java.util.function.BiConsumer<String, Map<String, Object>> consumer2 = (type, data) -> {};
        hook.addConsumer(consumer2);
        hook.removeConsumer(consumer2);
        hook.onEvent(new PreCallEvent(agent, List.of())).block();
        assertEquals(1, capturedEvents.size());
    }

    @Test
    void pipelineEvents() {
        hook.emitPipelineStart("p1", List.of("a1", "a2"));
        hook.emitPipelineStepStart("p1", 0, "agent1");
        hook.emitPipelineStepEnd("p1", 0, "agent1", 100L);
        hook.emitPipelineEnd("p1", 1, 200L);
        assertEquals(4, capturedEvents.size());
    }

    @Test
    void routingEvents() {
        hook.emitRoutingDecision("router", "agent1", "matched");
        hook.emitRoutingEnd("router", "agent1");
        assertEquals(2, capturedEvents.size());
    }

    @Test
    void handoffEvents() {
        hook.emitHandoffStart("from", "to", "intent");
        hook.emitHandoffComplete("from", "to");
        hook.emitHandoffError("from", "to", "err");
        assertEquals(3, capturedEvents.size());
    }

    @Test
    void loopEvents() {
        hook.emitLoopStart(1);
        hook.emitLoopIterationResult(1, false, "needs work");
        hook.emitLoopEnd(3, true);
        assertEquals(3, capturedEvents.size());
    }

    @Test
    void graphEvents() {
        hook.emitGraphAgentCall("REVIEWING", "reviewer");
        hook.emitGraphTransition("REVIEWING", "APPROVED", "decision");
        assertEquals(2, capturedEvents.size());
    }

    @Test
    void roundtableEvents() {
        hook.emitRoundtableStart("rt1", List.of("a1"), 3);
        hook.emitRoundStart(1);
        hook.emitRoundMessage("a1", "opinion");
        hook.emitRoundEnd(1);
        hook.emitRoundtableSummary("mod", "summary");
        assertEquals(5, capturedEvents.size());
    }

    @Test
    void taskEvents() {
        hook.emitTaskDelegate("from", "to", "task");
        hook.emitTaskStart("agent1");
        hook.emitTaskEnd("agent1", "result");
        hook.emitTaskAggregate(3);
        assertEquals(4, capturedEvents.size());
    }

    @Test
    void onEventExceptionIsCaught() {
        ObservabilityHook badHook = new ObservabilityHook();
        badHook.addConsumer((type, data) -> { throw new RuntimeException("consumer error"); });
        PreCallEvent event = new PreCallEvent(agent, List.of());
        assertNotNull(badHook.onEvent(event).block());
    }
}

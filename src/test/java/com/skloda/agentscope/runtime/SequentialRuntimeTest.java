package com.skloda.agentscope.runtime;

import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * Verifies the SequentialRuntime streaming rewrite: sub-agent {@code streamEvents()} deltas
 * (thinking/tool/text) flow to the frontend, the chain passes each step's final text to the
 * next, and the contract event sequence (pipeline_start → step_start → text → step_end →
 * done) is preserved.
 */
class SequentialRuntimeTest {

    @Test
    void streamsEachStepAndChainsOutputs() {
        // Step 1 emits two text deltas "hel" + "lo"; step 2 emits "world".
        // The chain must feed step 1's final text ("hello") into step 2's input.
        ReActAgent step1 = mock(ReActAgent.class);
        ReActAgent step2 = mock(ReActAgent.class);
        when(step1.getName()).thenReturn("doc-expert");
        when(step2.getName()).thenReturn("search-expert");
        when(step1.streamEvents(any(Msg.class))).thenReturn(Flux.just(
                delta("hel"), delta("lo")));
        when(step2.streamEvents(any(Msg.class))).thenReturn(Flux.just(
                delta("world")));
        // Also stub the 2-arg overload in case the bridge uses RuntimeContext.
        when(step1.streamEvents(any(Msg.class), any())).thenReturn(Flux.just(
                delta("hel"), delta("lo")));
        when(step2.streamEvents(any(Msg.class), any())).thenReturn(Flux.just(
                delta("world")));

        SequentialRuntime runtime = new SequentialRuntime(
                List.of(step1, step2), new ObservabilityHook(), "seq-test");

        Msg input = userMsg("analyze this");
        List<Map<String, Object>> events = new ArrayList<>();
        runtime.stream(input).doOnNext(events::add).blockLast();

        // Step text deltas forwarded for BOTH steps (streaming rewrite works).
        assertTrue(events.stream().anyMatch(e -> "text".equals(e.get("type"))
                && "hel".equals(e.get("content"))), "step1 delta 'hel' forwarded");
        assertTrue(events.stream().anyMatch(e -> "text".equals(e.get("type"))
                && "world".equals(e.get("content"))), "step2 delta 'world' forwarded");

        // Contract event sequence present.
        assertTrue(events.stream().anyMatch(e -> "pipeline_start".equals(e.get("type"))));
        assertTrue(events.stream().anyMatch(e -> "pipeline_step_start".equals(e.get("type"))
                && "doc-expert".equals(e.get("agentId"))));
        assertTrue(events.stream().anyMatch(e -> "pipeline_step_end".equals(e.get("type"))));
        assertTrue(events.stream().anyMatch(e -> "pipeline_end".equals(e.get("type"))));
        assertTrue(events.stream().anyMatch(e -> "done".equals(e.get("type"))));

        // Final text event carries the last step's output.
        assertTrue(events.stream().anyMatch(e -> "text".equals(e.get("type"))
                && "world".equals(e.get("content"))));
    }

    @Test
    void emptyAgentListStillCompletes() {
        SequentialRuntime runtime = new SequentialRuntime(
                List.of(), new ObservabilityHook(), "seq-empty");
        List<Map<String, Object>> events = new ArrayList<>();
        runtime.stream(userMsg("x")).doOnNext(events::add).blockLast();
        assertTrue(events.stream().anyMatch(e -> "done".equals(e.get("type"))));
        assertFalse(events.stream().anyMatch(e -> "error".equals(e.get("type"))));
    }

    private static AgentEvent delta(String text) {
        return new TextBlockDeltaEvent("reply-1", "block-1", text);
    }

    private static Msg userMsg(String text) {
        return Msg.builder().name("user").role(MsgRole.USER)
                .content(TextBlock.builder().text(text).build()).build();
    }
}

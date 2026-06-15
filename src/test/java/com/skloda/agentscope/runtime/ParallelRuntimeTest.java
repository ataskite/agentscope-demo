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

/**
 * Verifies ParallelRuntime: concurrent sub-agents each stream their deltas, results are
 * aggregated into a final text, and the contract event sequence is preserved.
 */
class ParallelRuntimeTest {

    @Test
    void runsAgentsConcurrentlyAndAggregates() {
        ReActAgent a = mock(ReActAgent.class);
        ReActAgent b = mock(ReActAgent.class);
        when(a.getName()).thenReturn("doc-expert");
        when(b.getName()).thenReturn("search-expert");
        when(a.streamEvents(any(Msg.class))).thenReturn(Flux.just(delta("doc-result")));
        when(b.streamEvents(any(Msg.class))).thenReturn(Flux.just(delta("search-result")));

        ParallelRuntime runtime = new ParallelRuntime(
                List.of(a, b), new ObservabilityHook(), "par-test");

        List<Map<String, Object>> events = new ArrayList<>();
        runtime.stream(userMsg("analyze")).doOnNext(events::add).blockLast();

        // Both agents' deltas forwarded.
        assertTrue(events.stream().anyMatch(e -> "text".equals(e.get("type"))
                && "doc-result".equals(e.get("content"))));
        assertTrue(events.stream().anyMatch(e -> "text".equals(e.get("type"))
                && "search-result".equals(e.get("content"))));

        // Aggregated final text contains both agent headers.
        assertTrue(events.stream().anyMatch(e -> "text".equals(e.get("type"))
                && String.valueOf(e.get("content")).contains("doc-expert")
                && String.valueOf(e.get("content")).contains("search-result")));

        // Contract sequence.
        assertTrue(events.stream().anyMatch(e -> "pipeline_start".equals(e.get("type"))));
        assertTrue(events.stream().anyMatch(e -> "pipeline_end".equals(e.get("type"))));
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

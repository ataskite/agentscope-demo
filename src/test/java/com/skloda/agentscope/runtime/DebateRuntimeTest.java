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
 * DebateRuntime tests — verifies streaming, the contract field "agent" (not "agentId")
 * on round_message/roundtable_summary, and the judge-synthesis final text.
 */
class DebateRuntimeTest {

    @Test
    void runsRoundsAndJudgeSynthesis() {
        ReActAgent expert1 = mock(ReActAgent.class);
        ReActAgent expert2 = mock(ReActAgent.class);
        ReActAgent judge = mock(ReActAgent.class);
        when(expert1.getName()).thenReturn("optimist");
        when(expert2.getName()).thenReturn("critic");
        when(judge.getName()).thenReturn("judge");
        when(expert1.streamEvents(any(Msg.class))).thenReturn(Flux.just(delta("pro-arg")));
        when(expert2.streamEvents(any(Msg.class))).thenReturn(Flux.just(delta("con-arg")));
        when(judge.streamEvents(any(Msg.class))).thenReturn(Flux.just(delta("balanced verdict")));

        DebateRuntime runtime = new DebateRuntime(
                List.of(expert1, expert2), judge, new ObservabilityHook(), "debate-test", 1);

        List<Map<String, Object>> events = new ArrayList<>();
        runtime.stream(userMsg("Is remote work good?")).doOnNext(events::add).blockLast();

        // Both experts' deltas forwarded (streaming works).
        assertTrue(events.stream().anyMatch(e -> "text".equals(e.get("type"))
                && "pro-arg".equals(e.get("content"))));
        assertTrue(events.stream().anyMatch(e -> "text".equals(e.get("type"))
                && "con-arg".equals(e.get("content"))));

        // round_message uses field "agent" (contract red line — NOT "agentId").
        assertTrue(events.stream().anyMatch(e -> "round_message".equals(e.get("type"))
                && "optimist".equals(e.get("agent"))));
        assertTrue(events.stream().anyMatch(e -> "round_message".equals(e.get("type"))
                && "critic".equals(e.get("agent"))));

        // Judge synthesis as final text + roundtable_summary.
        assertTrue(events.stream().anyMatch(e -> "text".equals(e.get("type"))
                && "balanced verdict".equals(e.get("content"))));
        assertTrue(events.stream().anyMatch(e -> "roundtable_summary".equals(e.get("type"))
                && "judge".equals(e.get("agent"))));
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

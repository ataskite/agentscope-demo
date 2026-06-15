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
 * MsgHubRuntime tests — verifies multi-round roundtable streaming and the contract field
 * "agent" on round_message/roundtable_summary.
 */
class MsgHubRuntimeTest {

    @Test
    void runsRoundsAndModeratorSummary() {
        ReActAgent architect = mock(ReActAgent.class);
        ReActAgent dba = mock(ReActAgent.class);
        ReActAgent moderator = mock(ReActAgent.class);
        when(architect.getName()).thenReturn("architect");
        when(dba.getName()).thenReturn("dba-expert");
        when(moderator.getName()).thenReturn("moderator");
        when(architect.streamEvents(any(Msg.class))).thenReturn(Flux.just(delta("use microservices")));
        when(dba.streamEvents(any(Msg.class))).thenReturn(Flux.just(delta("shard the DB")));
        when(moderator.streamEvents(any(Msg.class))).thenReturn(Flux.just(delta("consensus summary")));

        MsgHubRuntime runtime = new MsgHubRuntime(
                List.of(architect, dba), moderator, new ObservabilityHook(), "roundtable-test", 1);

        List<Map<String, Object>> events = new ArrayList<>();
        runtime.stream(userMsg("scale a system")).doOnNext(events::add).blockLast();

        // roundtable_start + round boundaries + round_message with field "agent".
        assertTrue(events.stream().anyMatch(e -> "roundtable_start".equals(e.get("type"))));
        assertTrue(events.stream().anyMatch(e -> "round_start".equals(e.get("type"))));
        assertTrue(events.stream().anyMatch(e -> "round_end".equals(e.get("type"))));
        assertTrue(events.stream().anyMatch(e -> "round_message".equals(e.get("type"))
                && "architect".equals(e.get("agent"))));
        assertTrue(events.stream().anyMatch(e -> "round_message".equals(e.get("type"))
                && "dba-expert".equals(e.get("agent"))));

        // Moderator summary as final text + roundtable_summary.
        assertTrue(events.stream().anyMatch(e -> "roundtable_summary".equals(e.get("type"))
                && "moderator".equals(e.get("agent"))));
        assertTrue(events.stream().anyMatch(e -> "text".equals(e.get("type"))
                && "consensus summary".equals(e.get("content"))));
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

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
 * LoopRuntime tests — including the regression for the max-iterations text-loss bug:
 * previously the final writer output was dropped when the critic never approved.
 */
class LoopRuntimeTest {

    @Test
    void approvedOnFirstIteration_emitsWriterOutputAsFinalText() {
        ReActAgent writer = mock(ReActAgent.class);
        ReActAgent critic = mock(ReActAgent.class);
        when(writer.getName()).thenReturn("writer");
        when(critic.getName()).thenReturn("critic");
        when(writer.streamEvents(any(Msg.class))).thenReturn(Flux.just(delta("draft v1")));
        when(critic.streamEvents(any(Msg.class))).thenReturn(Flux.just(delta("通过")));

        LoopRuntime runtime = new LoopRuntime(writer, critic, new ObservabilityHook(), "loop-test", 3);

        List<Map<String, Object>> events = new ArrayList<>();
        runtime.stream(userMsg("write a poem")).doOnNext(events::add).blockLast();

        // Final text = writer output (approved path).
        assertTrue(events.stream().anyMatch(e -> "text".equals(e.get("type"))
                && "draft v1".equals(e.get("content"))), "approved writer output emitted");
        assertTrue(events.stream().anyMatch(e -> "loop_end".equals(e.get("type"))
                && Boolean.TRUE.equals(e.get("approved"))));
        assertTrue(events.stream().anyMatch(e -> "done".equals(e.get("type"))));
        assertFalse(events.stream().anyMatch(e -> "error".equals(e.get("type"))));
    }

    @Test
    void maxIterationsReached_emitsLastWriterOutput_regression() {
        // Critic never approves; loop hits maxIterations=2.
        // REGRESSION: the last writer output MUST still be emitted as text (was dropped before).
        ReActAgent writer = mock(ReActAgent.class);
        ReActAgent critic = mock(ReActAgent.class);
        when(writer.getName()).thenReturn("writer");
        when(critic.getName()).thenReturn("critic");
        when(writer.streamEvents(any(Msg.class)))
                .thenReturn(Flux.just(delta("draft v1")))
                .thenReturn(Flux.just(delta("draft v2")));
        when(critic.streamEvents(any(Msg.class)))
                .thenReturn(Flux.just(delta("需要修改")))
                .thenReturn(Flux.just(delta("还是不行")));

        LoopRuntime runtime = new LoopRuntime(writer, critic, new ObservabilityHook(), "loop-test", 2);

        List<Map<String, Object>> events = new ArrayList<>();
        runtime.stream(userMsg("write a poem")).doOnNext(events::add).blockLast();

        // REGRESSION: final text present (the last writer output "draft v2").
        assertTrue(events.stream().anyMatch(e -> "text".equals(e.get("type"))
                        && "draft v2".equals(e.get("content"))),
                "max-iterations path must emit last writer output (was dropped before the fix)");
        assertTrue(events.stream().anyMatch(e -> "loop_end".equals(e.get("type"))
                && Boolean.FALSE.equals(e.get("approved"))));
        assertTrue(events.stream().anyMatch(e -> "done".equals(e.get("type"))));
    }

    private static AgentEvent delta(String text) {
        return new TextBlockDeltaEvent("reply-1", "block-1", text);
    }

    private static Msg userMsg(String text) {
        return Msg.builder().name("user").role(MsgRole.USER)
                .content(TextBlock.builder().text(text).build()).build();
    }
}

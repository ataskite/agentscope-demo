package com.skloda.agentscope.runtime;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the shared multi-agent streaming bridge.
 * Locks: extractText behavior, runSubAgent forwarding + source tagging, AGENT_RESULT
 * preference over accumulated text deltas, and error handling.
 */
class MultiAgentStreamSupportTest {

    @Test
    void extractText_concatenatesTextBlocks() {
        // A Msg carries a list of content blocks; extractText concatenates the TextBlocks.
        Msg msg = Msg.builder().role(MsgRole.ASSISTANT)
                .content(List.of(
                        TextBlock.builder().text("hello ").build(),
                        TextBlock.builder().text("world").build()))
                .build();
        assertEquals("hello world", MultiAgentStreamSupport.extractText(msg));
    }

    @Test
    void extractText_nullMsg_returnsEmpty() {
        assertEquals("", MultiAgentStreamSupport.extractText(null));
    }

    @Test
    void runSubAgent_forwardsMappedEventsWithSourceTagAndResolvesToFinalText() {
        ReActAgent agent = mock(ReActAgent.class);
        // A thinking delta + a text delta + an AGENT_RESULT carrying the authoritative text.
        Msg resultMsg = Msg.builder().role(MsgRole.ASSISTANT)
                .content(List.of(TextBlock.builder().text("final").build())).build();
        when(agent.streamEvents(any(Msg.class))).thenReturn(Flux.just(
                new ThinkingBlockDeltaEvent("r", "b", "thinking..."),
                new TextBlockDeltaEvent("r", "b", "par"),
                new AgentResultEvent(resultMsg)));

        List<Map<String, Object>> forwarded = new ArrayList<>();
        FluxSink<Map<String, Object>> sink = mockSink(forwarded);

        Msg input = Msg.builder().role(MsgRole.USER).textContent("hi").build();
        StepVerifier.create(MultiAgentStreamSupport.runSubAgent(agent, input, "expert-A", sink, null))
                .expectNext("final")  // AGENT_RESULT preferred over accumulated "par"
                .verifyComplete();

        // All three mapped events forwarded, each tagged with the source label.
        assertEquals(3, forwarded.size());
        assertEquals("thinking", forwarded.get(0).get("type"));
        assertEquals("expert-A", forwarded.get(0).get("source"));
        assertEquals("text", forwarded.get(1).get("type"));
        assertEquals("par", forwarded.get(1).get("content"));
        assertEquals("expert-A", forwarded.get(1).get("source"));
        assertEquals("agent_result_text", forwarded.get(2).get("type"));
    }

    @Test
    void runSubAgent_fallsBackToAccumulatedTextWhenNoAgentResult() {
        ReActAgent agent = mock(ReActAgent.class);
        // No AGENT_RESULT — only text deltas; the bridge accumulates them.
        when(agent.streamEvents(any(Msg.class))).thenReturn(Flux.just(
                new TextBlockDeltaEvent("r", "b", "hel"),
                new TextBlockDeltaEvent("r", "b", "lo")));

        List<Map<String, Object>> forwarded = new ArrayList<>();
        FluxSink<Map<String, Object>> sink = mockSink(forwarded);

        Msg input = Msg.builder().role(MsgRole.USER).textContent("hi").build();
        StepVerifier.create(MultiAgentStreamSupport.runSubAgent(agent, input, "x", sink, null))
                .expectNext("hello")  // accumulated from deltas
                .verifyComplete();
    }

    @Test
    void runSubAgent_emitsErrorAndResolvesEmptyOnError() {
        ReActAgent agent = mock(ReActAgent.class);
        when(agent.streamEvents(any(Msg.class))).thenReturn(Flux.error(new RuntimeException("boom")));

        List<Map<String, Object>> forwarded = new ArrayList<>();
        FluxSink<Map<String, Object>> sink = mockSink(forwarded);

        Msg input = Msg.builder().role(MsgRole.USER).textContent("hi").build();
        StepVerifier.create(MultiAgentStreamSupport.runSubAgent(agent, input, "x", sink, null))
                .expectNext("")
                .verifyComplete();

        assertEquals(1, forwarded.size());
        assertEquals("error", forwarded.get(0).get("type"));
    }

    /** Mockito-stubbed FluxSink that records everything pushed to it. */
    @SuppressWarnings("unchecked")
    private static FluxSink<Map<String, Object>> mockSink(List<Map<String, Object>> into) {
        FluxSink<Map<String, Object>> sink = mock(FluxSink.class);
        doAnswer(inv -> {
            into.add(inv.getArgument(0));
            return sink;
        }).when(sink).next(any());
        return sink;
    }
}

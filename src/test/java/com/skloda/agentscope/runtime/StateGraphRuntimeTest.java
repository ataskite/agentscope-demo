package com.skloda.agentscope.runtime;

import com.skloda.agentscope.composite.graph.OrderFulfillmentGraph;
import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StateGraphRuntimeTest {

    private OrderFulfillmentGraph mockGraph;
    // Use a real ObservabilityHook so the EventSink bridge is exercised end-to-end
    // (matching StructuredOutputAgentRuntimeTest). Mocking the hook leaves
    // getEventSink() returning null, which NPEs after the EventSink migration.
    private ObservabilityHook hook;
    private StateGraphRuntime runtime;

    @BeforeEach
    void setUp() {
        mockGraph = mock(OrderFulfillmentGraph.class);
        hook = new ObservabilityHook();

        runtime = new StateGraphRuntime("test-graph", mockGraph, hook);
    }

    @AfterEach
    void tearDown() {
        if (runtime != null) {
            runtime.close();
        }
    }

    @Test
    void constructor_initializesGraphAndHook() {
        assertNotNull(runtime);
    }

    @Test
    void getHook_returnsHook() {
        assertSame(hook, runtime.getHook());
    }

    @Test
    void close_completesEventSink() {
        // After the EventSink migration, ObservabilityHook.removeConsumer/reset are no-ops;
        // close() now completes the underlying EventSink. Subscribers should see completion.
        List<Map<String, Object>> received = new ArrayList<>();
        hook.getEventSink().asFlux().subscribe(received::add, t -> {}, () -> {});

        runtime.close();

        // No events were emitted; the sink simply completes cleanly without throwing.
        assertTrue(received.isEmpty());
    }

    @Test
    void stream_emitsTextBlocks() {
        Msg inputMsg = Msg.builder().role(MsgRole.USER).content(TextBlock.builder().text("test message").build()).build();
        Msg resultMsg = Msg.builder().role(MsgRole.ASSISTANT).content(TextBlock.builder().text("test response").build()).build();

        when(mockGraph.execute(any(Msg.class))).thenReturn(Mono.just(resultMsg));

        List<Map<String, Object>> results = new ArrayList<>();
        runtime.stream(inputMsg)
            .doOnNext(results::add)
            .blockLast();

        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(m -> "text".equals(m.get("type"))));
        assertTrue(results.stream().anyMatch(m -> "done".equals(m.get("type"))));
    }

    @Test
    void stream_handlesNullResult() {
        Msg inputMsg = Msg.builder().role(MsgRole.USER).content(TextBlock.builder().text("test message").build()).build();

        when(mockGraph.execute(any(Msg.class))).thenReturn(Mono.empty());

        List<Map<String, Object>> results = new ArrayList<>();
        runtime.stream(inputMsg)
            .doOnNext(results::add)
            .blockLast();

        assertTrue(results.stream().anyMatch(m -> "done".equals(m.get("type"))));
    }

    @Test
    void stream_handlesError() {
        Msg inputMsg = Msg.builder().role(MsgRole.USER).content(TextBlock.builder().text("test message").build()).build();

        when(mockGraph.execute(any(Msg.class))).thenReturn(Mono.error(new RuntimeException("test error")));

        List<Map<String, Object>> results = new ArrayList<>();
        runtime.stream(inputMsg)
            .doOnNext(results::add)
            .blockLast();

        assertTrue(results.stream().anyMatch(m -> "error".equals(m.get("type"))));
        Map<String, Object> errorEvent = results.stream()
            .filter(m -> "error".equals(m.get("type")))
            .findFirst()
            .orElse(null);
        assertNotNull(errorEvent);
        assertEquals("test error", errorEvent.get("message"));
    }
}

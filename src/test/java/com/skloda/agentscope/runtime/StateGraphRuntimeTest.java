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
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StateGraphRuntimeTest {

    private OrderFulfillmentGraph mockGraph;
    private ObservabilityHook mockHook;
    private StateGraphRuntime runtime;

    @BeforeEach
    void setUp() {
        mockGraph = mock(OrderFulfillmentGraph.class);
        mockHook = mock(ObservabilityHook.class);

        runtime = new StateGraphRuntime("test-graph", mockGraph, mockHook);
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
        assertSame(mockHook, runtime.getHook());
    }

    @Test
    void close_removesHookConsumer() {
        runtime.close();
        verify(mockHook).removeConsumer(any());
        verify(mockHook).reset();
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

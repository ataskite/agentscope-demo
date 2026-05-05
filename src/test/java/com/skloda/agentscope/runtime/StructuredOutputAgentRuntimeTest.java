package com.skloda.agentscope.runtime;

import com.skloda.agentscope.hook.ObservabilityHook;
import com.skloda.agentscope.schema.InvoiceData;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StructuredOutputAgentRuntimeTest {

    @Test
    void invalidStructuredOutputIsRetriedAndEmitsValidationEvents() {
        ReActAgent agent = mock(ReActAgent.class);
        when(agent.getName()).thenReturn("invoice-extractor");

        InvoiceData incomplete = new InvoiceData();
        incomplete.invoiceNumber = "INV-001";
        incomplete.totalAmount = 100.0;

        InvoiceData repaired = new InvoiceData();
        repaired.invoiceNumber = "INV-001";
        repaired.invoiceDate = "2026-04-30";
        repaired.totalAmount = 100.0;
        repaired.sellerName = "销售方";
        repaired.buyerName = "购买方";

        when(agent.call(any(Msg.class), eq(InvoiceData.class)))
                .thenReturn(Mono.just(responseWithStructuredOutput(incomplete)))
                .thenReturn(Mono.just(responseWithStructuredOutput(repaired)));

        StructuredOutputAgentRuntime runtime = new StructuredOutputAgentRuntime(
                agent,
                new ObservabilityHook(),
                InvoiceData.class.getName());

        List<Map<String, Object>> events = runtime.stream(Msg.builder()
                        .name("user")
                        .role(MsgRole.USER)
                        .textContent("提取发票")
                        .build())
                .takeUntil(event -> "done".equals(event.get("type")))
                .collectList()
                .block();

        assertTrue(events.stream().anyMatch(event -> "structured_validation_failed".equals(event.get("type"))));
        assertTrue(events.stream().anyMatch(event -> "structured_repair_start".equals(event.get("type"))));
        assertTrue(events.stream().anyMatch(event -> "structured_validation_passed".equals(event.get("type"))));
        assertTrue(events.stream().anyMatch(event ->
                "structured_data".equals(event.get("type"))
                        && event.get("data").toString().contains("\"sellerName\":\"销售方\"")));
        assertEquals("done", events.get(events.size() - 1).get("type"));
        verify(agent, times(2)).call(any(Msg.class), eq(InvoiceData.class));
    }

    private static Msg responseWithStructuredOutput(Object structuredData) {
        return Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .textContent("已提取")
                .metadata(Map.of("_structured_output", structuredData))
                .build();
    }
}

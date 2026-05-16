package com.skloda.agentscope.harness;

import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HarnessRuntimeTest {

    @Test
    void convertsTextEvent() {
        Msg msg = Msg.builder()
                .content(List.of(TextBlock.builder().text("Hello").build()))
                .build();
        Event event = new Event(EventType.AGENT_RESULT, msg, false);

        Map<String, Object> result = HarnessRuntime.convertEvent(event);
        assertEquals("text", result.get("type"));
        assertEquals("Hello", result.get("content"));
    }

    @Test
    void convertsUnknownEventToRaw() {
        Event event = new Event(EventType.REASONING, null, false);

        Map<String, Object> result = HarnessRuntime.convertEvent(event);
        assertEquals("raw_event", result.get("type"));
    }
}

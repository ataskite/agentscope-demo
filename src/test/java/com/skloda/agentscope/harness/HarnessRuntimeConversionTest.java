package com.skloda.agentscope.harness;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentType;
import com.skloda.agentscope.agent.HarnessConfig;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HarnessRuntimeConversionTest {

    @Test
    void convertsTextEventWithContent() {
        Msg msg = Msg.builder()
                .content(List.of(TextBlock.builder().text("Hello world").build()))
                .build();
        Event event = new Event(EventType.AGENT_RESULT, msg, false);

        Map<String, Object> result = HarnessRuntime.convertEvent(event);

        assertEquals("text", result.get("type"));
        assertEquals("Hello world", result.get("content"));
    }

    @Test
    void convertsAgentResultWithEmptyContent() {
        Msg msg = Msg.builder()
                .content(TextBlock.builder().text("").build())
                .build();
        Event event = new Event(EventType.AGENT_RESULT, msg, false);

        Map<String, Object> result = HarnessRuntime.convertEvent(event);

        // Empty text returns empty event to avoid breaking stream
        assertEquals("empty", result.get("type"));
    }

    @Test
    void convertsAgentResultWithNullMessage() {
        Msg msg = Msg.builder().build();
        Event event = new Event(EventType.AGENT_RESULT, msg, false);

        Map<String, Object> result = HarnessRuntime.convertEvent(event);

        // Empty message returns empty event
        assertEquals("empty", result.get("type"));
    }

    @Test
    void convertsAgentResultWithNullContent() {
        Msg msg = Msg.builder().build();
        Event event = new Event(EventType.AGENT_RESULT, msg, false);

        Map<String, Object> result = HarnessRuntime.convertEvent(event);

        // Empty message returns empty event
        assertEquals("empty", result.get("type"));
    }

    @Test
    void convertsNonAgentResultEvent() {
        Event event = new Event(EventType.REASONING, null, false);

        Map<String, Object> result = HarnessRuntime.convertEvent(event);

        // No message returns empty event
        assertEquals("empty", result.get("type"));
    }

    @Test
    void convertsToolCallEvent() {
        Msg msg = Msg.builder()
                .content(TextBlock.builder().text("tool output").build())
                .build();
        Event event = new Event(EventType.TOOL_RESULT, msg, false);

        Map<String, Object> result = HarnessRuntime.convertEvent(event);

        // Events with text content are mapped to text
        assertEquals("text", result.get("type"));
        assertEquals("tool output", result.get("content"));
    }

    @Test
    void convertsMultipleTextBlocksConcatenated() {
        Msg msg = Msg.builder()
                .content(List.of(
                        TextBlock.builder().text("Part 1 ").build(),
                        TextBlock.builder().text("Part 2").build()
                ))
                .build();
        Event event = new Event(EventType.AGENT_RESULT, msg, false);

        Map<String, Object> result = HarnessRuntime.convertEvent(event);

        assertEquals("text", result.get("type"));
        assertEquals("Part 1 Part 2", result.get("content"));
    }
}

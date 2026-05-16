package com.skloda.agentscope.harness;

import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;

public class HarnessRuntime {

    private static final Logger log = LoggerFactory.getLogger(HarnessRuntime.class);

    private final HarnessAgent agent;

    public HarnessRuntime(HarnessAgent agent) {
        this.agent = agent;
    }

    public Flux<Map<String, Object>> stream(Msg userMsg, RuntimeContext ctx) {
        return agent.stream(userMsg, ctx)
                .map(HarnessRuntime::convertEvent)
                .doOnNext(event -> log.debug("[harness] event: {}", event.get("type")));
    }

    static Map<String, Object> convertEvent(Event event) {
        EventType type = event.getType();
        Msg msg = event.getMessage();

        if (type == EventType.AGENT_RESULT && msg != null && msg.getContent() != null) {
            StringBuilder text = new StringBuilder();
            for (ContentBlock block : msg.getContent()) {
                if (block instanceof TextBlock tb && tb.getText() != null && !tb.getText().isEmpty()) {
                    text.append(tb.getText());
                }
            }
            if (text.length() > 0) {
                return Map.of("type", "text", "content", text.toString());
            }
        }

        // Generic event passthrough
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "raw_event");
        result.put("eventType", type != null ? type.name() : "UNKNOWN");
        if (msg != null && msg.getContent() != null) {
            for (ContentBlock block : msg.getContent()) {
                if (block instanceof TextBlock tb) {
                    result.put("content", tb.getText());
                    break;
                }
            }
        }
        return result;
    }
}

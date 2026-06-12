package com.skloda.agentscope.harness;

import com.skloda.agentscope.hook.ObservabilityHook;
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

public class HarnessRuntime implements com.skloda.agentscope.runtime.StreamingAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(HarnessRuntime.class);

    private final HarnessAgent agent;
    private final RuntimeContext runtimeContext;
    private final ObservabilityHook hook = new ObservabilityHook();

    public HarnessRuntime(HarnessAgent agent, RuntimeContext runtimeContext) {
        this.agent = agent;
        this.runtimeContext = runtimeContext;
    }

    public HarnessRuntime(HarnessAgent agent) {
        this(agent, RuntimeContext.builder().build());
    }

    @Override
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        return agent.stream(userMsg, runtimeContext)
                .map(HarnessRuntime::convertEvent)
                .doOnNext(event -> log.debug("[harness] event: {}", event.get("type")))
                .concatWith(Flux.just(Map.of("type", "done")));
    }

    @Override
    public ObservabilityHook getHook() {
        return hook;
    }

    @Override
    public void close() {
        // No resources to clean up
    }

    static Map<String, Object> convertEvent(Event event) {
        EventType type = event.getType();
        Msg msg = event.getMessage();

        // Map REASONING events to text for frontend display
        if (type == EventType.REASONING && msg != null && msg.getContent() != null) {
            for (ContentBlock block : msg.getContent()) {
                if (block instanceof TextBlock tb && tb.getText() != null && !tb.getText().isEmpty()) {
                    return Map.of("type", "text", "content", tb.getText());
                }
            }
        }

        // Map AGENT_RESULT events to text
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

        // For other event types, extract text content and map to text
        if (msg != null && msg.getContent() != null) {
            for (ContentBlock block : msg.getContent()) {
                if (block instanceof TextBlock tb && tb.getText() != null && !tb.getText().isEmpty()) {
                    return Map.of("type", "text", "content", tb.getText());
                }
            }
        }

        // Empty event to avoid breaking the stream
        return Map.of("type", "empty");
    }
}

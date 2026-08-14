package com.skloda.agentscope.harness;

import com.skloda.agentscope.hook.ObservabilityHook;
import com.skloda.agentscope.runtime.AgentEventMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Streaming runtime for {@link HarnessAgent}.
 * <p>
 * Uses {@code agent.streamEvents(Msg, RuntimeContext)} (the non-deprecated AgentScope 2.0 event
 * API, returning {@code Flux<AgentEvent>}) and reuses the shared {@link AgentEventMapper} to
 * convert each {@link AgentEvent} into an SSE-compatible {@code Map}. This is the same mapping
 * pipeline used by {@code AgentRuntime} for single agents, so harness agents now emit the full
 * typed event stream (text deltas, thinking, tool_start/end, tool_result, …) rather than a
 * flattened text-only stream.
 * <p>
 * <b>History:</b> this runtime previously called the deprecated {@code agent.stream()} (returning
 * {@code Flux<io.agentscope.core.agent.Event>}) as a workaround for the GA gap where
 * {@code streamEvents()} did not forward sub-agent events. That gap was fixed in agentscope 2.0.2
 * (PR #2613), so {@code streamEvents()} is now the correct path and surfaces remote/sub-agent
 * events too.
 */
public class HarnessRuntime implements com.skloda.agentscope.runtime.StreamingAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(HarnessRuntime.class);
    private static final AgentEventMapper eventMapper = new AgentEventMapper();

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
        return agent.streamEvents(userMsg, runtimeContext)
                // Use handle() (not map()) because AgentEventMapper.apply() intentionally returns
                // null for events the frontend does not need (block boundaries); map() would throw.
                // Type witness <Map<String,Object>> is required so doOnNext sees Map, not Object.
                .<Map<String, Object>>handle((event, sink) -> {
                    Map<String, Object> map = eventMapper.apply(event);
                    if (map != null && !map.isEmpty()) {
                        sink.next(map);
                    }
                })
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
}

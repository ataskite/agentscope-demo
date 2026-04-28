package com.skloda.agentscope.runtime;

import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.message.Msg;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Common streaming contract for both single-agent and composite-agent runtimes.
 */
public interface StreamingAgentRuntime extends AutoCloseable {

    Flux<Map<String, Object>> stream(Msg userMsg);

    ObservabilityHook getHook();

    @Override
    void close();
}

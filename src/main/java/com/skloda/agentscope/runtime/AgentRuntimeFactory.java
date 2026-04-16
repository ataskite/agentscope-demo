package com.skloda.agentscope.runtime;

import com.skloda.agentscope.agent.AgentFactory;
import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.ReActAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for creating AgentRuntime instances.
 * Wraps AgentFactory to produce AgentRuntime with fresh Hook each time.
 */
@Component
public class AgentRuntimeFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeFactory.class);

    private final AgentFactory agentFactory;

    public AgentRuntimeFactory(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    /**
     * Create a new AgentRuntime with fresh Hook for the given agentId.
     */
    public AgentRuntime createRuntime(String agentId) {
        log.debug("Creating AgentRuntime for agent: {}", agentId);

        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = agentFactory.createAgent(agentId, hook);

        return new AgentRuntime(agent, hook);
    }
}

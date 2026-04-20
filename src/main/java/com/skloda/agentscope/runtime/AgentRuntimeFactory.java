package com.skloda.agentscope.runtime;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentType;
import com.skloda.agentscope.composite.CompositeAgentFactory;
import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.Memory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for creating AgentRuntime instances.
 * Uses CompositeAgentFactory to support both single and composite agent types.
 */
@Component
public class AgentRuntimeFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeFactory.class);

    private final CompositeAgentFactory compositeFactory;
    private final AgentConfigService configService;

    public AgentRuntimeFactory(CompositeAgentFactory compositeFactory,
                                AgentConfigService configService) {
        this.compositeFactory = compositeFactory;
        this.configService = configService;
    }

    /**
     * Create a new AgentRuntime with a fresh agent (stateless mode).
     * Supports SINGLE agent type; composite types are not yet supported in stateless mode.
     */
    public AgentRuntime createRuntime(String agentId) {
        log.debug("Creating AgentRuntime for agent: {}", agentId);

        AgentConfig config = configService.getAgentConfig(agentId);
        AgentType type = config.getType() != null ? config.getType() : AgentType.SINGLE;

        if (type == AgentType.SINGLE) {
            return createSingleRuntime(agentId);
        }

        // Composite types: will be handled by future tasks
        throw new UnsupportedOperationException(
                "Agent type " + type + " is not yet supported in stateless mode");
    }

    /**
     * Create AgentRuntime with an existing memory (session mode).
     * A new agent is created per request but shares the same memory instance,
     * so conversation history persists across requests.
     */
    public AgentRuntime createRuntimeWithMemory(String agentId, Memory memory) {
        log.debug("Creating AgentRuntime with shared memory for agent: {}", agentId);

        AgentConfig config = configService.getAgentConfig(agentId);
        AgentType type = config.getType() != null ? config.getType() : AgentType.SINGLE;

        if (type == AgentType.SINGLE) {
            return createSingleRuntimeWithMemory(agentId, memory);
        }

        // Composite types: will be handled by future tasks
        throw new UnsupportedOperationException(
                "Agent type " + type + " is not yet supported in session mode");
    }

    /**
     * Get the composite factory for direct use by composite agent runtimes.
     */
    public CompositeAgentFactory getCompositeFactory() {
        return compositeFactory;
    }

    /**
     * Get the config service for looking up agent configurations.
     */
    public AgentConfigService getConfigService() {
        return configService;
    }

    private AgentRuntime createSingleRuntime(String agentId) {
        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createSingleAgent(agentId, hook);
        return new AgentRuntime(agent, hook);
    }

    private AgentRuntime createSingleRuntimeWithMemory(String agentId, Memory memory) {
        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createSingleAgentForSession(agentId, memory, hook);
        return new AgentRuntime(agent, hook);
    }
}

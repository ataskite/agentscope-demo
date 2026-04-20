package com.skloda.agentscope.composite;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentFactory;
import com.skloda.agentscope.agent.AgentType;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.pipeline.FanoutPipeline;
import io.agentscope.core.pipeline.SequentialPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Factory for creating multi-agent compositions.
 * Dispatches by AgentConfig.type() to the appropriate creation strategy.
 *
 * SINGLE agents delegate to the existing AgentFactory.
 * SEQUENTIAL/PARALLEL agents use AgentScope pipelines.
 * ROUTING/HANDOFFS agents use SubAgentTool for dynamic dispatch.
 */
@Component
public class CompositeAgentFactory {
    private static final Logger log = LoggerFactory.getLogger(CompositeAgentFactory.class);

    private final AgentFactory singleAgentFactory;
    private final AgentConfigService configService;

    @Value("${agentscope.model.dashscope.api-key:}")
    private String apiKey;

    public CompositeAgentFactory(AgentFactory singleAgentFactory, AgentConfigService configService) {
        this.singleAgentFactory = singleAgentFactory;
        this.configService = configService;
    }

    /**
     * Create a single (SINGLE-type) agent using the existing AgentFactory.
     * Delegates to AgentFactory.createAgent(agentId, hooks).
     */
    public ReActAgent createSingleAgent(String agentId, Hook... hooks) {
        return singleAgentFactory.createAgent(agentId, hooks);
    }

    /**
     * Create a single agent for session use (with externally provided memory).
     */
    public ReActAgent createSingleAgentForSession(String agentId, Memory memory, Hook... hooks) {
        return singleAgentFactory.createAgentForSession(agentId, memory, hooks);
    }

    /**
     * Create memory for a given agentId.
     */
    public Memory createMemory(String agentId) {
        return singleAgentFactory.createMemory(agentId);
    }

    /**
     * Create sub-agents as AgentBase list for pipeline use.
     * Each sub-agent is created using AgentFactory with a fresh InMemoryMemory.
     */
    public List<AgentBase> createSubAgents(AgentConfig config) {
        return config.getSubAgents().stream()
                .map(sub -> {
                    log.info("Creating sub-agent: {} for composite: {}", sub.getAgentId(), config.getAgentId());
                    return singleAgentFactory.createAgent(sub.getAgentId());
                })
                .map(ReActAgent.class::cast)
                .map(AgentBase.class::cast)
                .toList();
    }

    /**
     * Create sub-agents with shared memory for pipeline use.
     */
    public List<AgentBase> createSubAgentsWithMemory(AgentConfig config, Memory memory) {
        return config.getSubAgents().stream()
                .map(sub -> {
                    log.info("Creating sub-agent: {} for composite: {} (shared memory)", sub.getAgentId(), config.getAgentId());
                    return singleAgentFactory.createAgentForSession(sub.getAgentId(), memory);
                })
                .map(ReActAgent.class::cast)
                .map(AgentBase.class::cast)
                .toList();
    }

    // Stub methods for Tasks 11-13
    /**
     * Create a sequential pipeline of sub-agents.
     * Each sub-agent processes the message in order; the output of one feeds into the next.
     */
    public SequentialPipeline createSequentialAgent(AgentConfig config, Memory memory) {
        if (config.getSubAgents() == null || config.getSubAgents().isEmpty()) {
            throw new IllegalArgumentException("SEQUENTIAL agent requires at least one sub-agent: " + config.getAgentId());
        }

        log.info("Creating SEQUENTIAL pipeline for: {} with {} sub-agents", config.getAgentId(), config.getSubAgents().size());

        List<AgentBase> subAgents = (memory != null)
                ? createSubAgentsWithMemory(config, memory)
                : createSubAgents(config);

        return SequentialPipeline.builder()
                .addAgents(subAgents)
                .build();
    }

    /**
     * Create a parallel (fanout) pipeline of sub-agents.
     * All sub-agents receive the same message and execute concurrently.
     * Uses config.parallel flag to determine concurrent vs sequential fanout.
     */
    public FanoutPipeline createParallelAgent(AgentConfig config, Memory memory) {
        if (config.getSubAgents() == null || config.getSubAgents().isEmpty()) {
            throw new IllegalArgumentException("PARALLEL agent requires at least one sub-agent: " + config.getAgentId());
        }

        boolean concurrent = config.getParallel() != null ? config.getParallel() : true;
        log.info("Creating PARALLEL pipeline for: {} with {} sub-agents (concurrent={})",
                config.getAgentId(), config.getSubAgents().size(), concurrent);

        List<AgentBase> subAgents = (memory != null)
                ? createSubAgentsWithMemory(config, memory)
                : createSubAgents(config);

        FanoutPipeline.Builder builder = FanoutPipeline.builder()
                .addAgents(subAgents)
                .concurrent(concurrent);

        return builder.build();
    }

    public ReActAgent createRoutingAgent(AgentConfig config, Memory memory, Hook... hooks) {
        throw new UnsupportedOperationException("ROUTING agent creation not yet implemented");
    }

    public ReActAgent createHandoffsAgent(AgentConfig config, Memory memory, Hook... hooks) {
        throw new UnsupportedOperationException("HANDOFFS agent creation not yet implemented");
    }
}

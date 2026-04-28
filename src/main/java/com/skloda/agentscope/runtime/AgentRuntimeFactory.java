package com.skloda.agentscope.runtime;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentType;
import com.skloda.agentscope.composite.CompositeAgentFactory;
import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.pipeline.FanoutPipeline;
import io.agentscope.core.pipeline.SequentialPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for creating AgentRuntime instances.
 * Uses CompositeAgentFactory to support both single and composite agent types.
 *
 * SINGLE agents produce AgentRuntime (wrapping ReActAgent).
 * SEQUENTIAL/PARALLEL agents produce PipelineAgentRuntime (wrapping Pipeline).
 * ROUTING/HANDOFFS agents will produce AgentRuntime (wrapping ReActAgent with SubAgentTools).
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
     * Create a new runtime for stateless agent interaction.
     */
    public StreamingAgentRuntime createRuntime(String agentId) {
        log.debug("Creating AgentRuntime for agent: {}", agentId);

        AgentConfig config = configService.getAgentConfig(agentId);
        AgentType type = config.getType() != null ? config.getType() : AgentType.SINGLE;

        return switch (type) {
            case SINGLE -> createSingleRuntime(agentId);
            case SEQUENTIAL -> createSequentialRuntime(agentId);
            case PARALLEL -> createParallelRuntime(agentId);
            case ROUTING -> createRoutingRuntime(agentId);
            case HANDOFFS -> createHandoffsRuntime(agentId);
        };
    }

    /**
     * Create runtime with shared memory (session mode).
     */
    public StreamingAgentRuntime createRuntimeWithMemory(String agentId, Memory memory) {
        log.debug("Creating AgentRuntime with shared memory for agent: {}", agentId);

        AgentConfig config = configService.getAgentConfig(agentId);
        AgentType type = config.getType() != null ? config.getType() : AgentType.SINGLE;

        return switch (type) {
            case SINGLE -> createSingleRuntimeWithMemory(agentId, memory);
            case SEQUENTIAL -> createSequentialRuntimeWithMemory(agentId, memory);
            case PARALLEL -> createParallelRuntimeWithMemory(agentId, memory);
            case ROUTING -> createRoutingRuntimeWithMemory(agentId, memory);
            case HANDOFFS -> createHandoffsRuntimeWithMemory(agentId, memory);
        };
    }

    /**
     * Create a pipeline runtime for sequential agents (stateless mode).
     * Returns PipelineAgentRuntime which provides the same Flux interface.
     */
    public PipelineAgentRuntime createSequentialRuntime(String agentId) {
        log.debug("Creating SequentialRuntime for agent: {}", agentId);

        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        SequentialPipeline pipeline = compositeFactory.createSequentialAgent(config, null);

        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    /**
     * Create a pipeline runtime for sequential agents (session mode with shared memory).
     */
    public PipelineAgentRuntime createSequentialRuntimeWithMemory(String agentId, Memory memory) {
        log.debug("Creating SequentialRuntime with shared memory for agent: {}", agentId);

        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        SequentialPipeline pipeline = compositeFactory.createSequentialAgent(config, memory);

        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    /**
     * Create a pipeline runtime for parallel (fanout) agents (stateless mode).
     */
    public PipelineAgentRuntime createParallelRuntime(String agentId) {
        log.debug("Creating ParallelRuntime for agent: {}", agentId);

        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        FanoutPipeline pipeline = compositeFactory.createParallelAgent(config, null);

        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    /**
     * Create a pipeline runtime for parallel (fanout) agents (session mode with shared memory).
     */
    public PipelineAgentRuntime createParallelRuntimeWithMemory(String agentId, Memory memory) {
        log.debug("Creating ParallelRuntime with shared memory for agent: {}", agentId);

        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        FanoutPipeline pipeline = compositeFactory.createParallelAgent(config, memory);

        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    /**
     * Create a runtime for routing agents (stateless mode).
     * Routing agents are ReActAgents with SubAgentTools, so they use AgentRuntime.
     */
    public AgentRuntime createRoutingRuntime(String agentId) {
        log.debug("Creating RoutingRuntime for agent: {}", agentId);

        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createRoutingAgent(
                configService.getAgentConfig(agentId), null, hook);

        return new AgentRuntime(agent, hook);
    }

    /**
     * Create a runtime for routing agents (session mode with shared memory).
     */
    public AgentRuntime createRoutingRuntimeWithMemory(String agentId, Memory memory) {
        log.debug("Creating RoutingRuntime with shared memory for agent: {}", agentId);

        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createRoutingAgent(
                configService.getAgentConfig(agentId), memory, hook);

        return new AgentRuntime(agent, hook);
    }

    /**
     * Create a runtime for handoffs agents (stateless mode).
     * Handoffs agents are ReActAgents with SubAgentTools and trigger-based routing.
     */
    public AgentRuntime createHandoffsRuntime(String agentId) {
        log.debug("Creating HandoffsRuntime for agent: {}", agentId);

        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createHandoffsAgent(
                configService.getAgentConfig(agentId), null, hook);

        return new AgentRuntime(agent, hook);
    }

    /**
     * Create a runtime for handoffs agents (session mode with shared memory).
     */
    public AgentRuntime createHandoffsRuntimeWithMemory(String agentId, Memory memory) {
        log.debug("Creating HandoffsRuntime with shared memory for agent: {}", agentId);

        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createHandoffsAgent(
                configService.getAgentConfig(agentId), memory, hook);

        return new AgentRuntime(agent, hook);
    }

    public CompositeAgentFactory getCompositeFactory() {
        return compositeFactory;
    }

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

package com.skloda.agentscope.runtime;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentType;
import com.skloda.agentscope.composite.CompositeAgentFactory;
import com.skloda.agentscope.hook.ApprovalHook;
import com.skloda.agentscope.hook.ObservabilityHook;
import com.skloda.agentscope.service.ApprovalService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.pipeline.FanoutPipeline;
import io.agentscope.core.pipeline.SequentialPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AgentRuntimeFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeFactory.class);

    private final CompositeAgentFactory compositeFactory;
    private final AgentConfigService configService;
    private final ApprovalService approvalService;

    public AgentRuntimeFactory(CompositeAgentFactory compositeFactory,
                                AgentConfigService configService,
                                ApprovalService approvalService) {
        this.compositeFactory = compositeFactory;
        this.configService = configService;
        this.approvalService = approvalService;
    }

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
            case DEBATE -> createDebateRuntime(agentId);
        };
    }

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
            case DEBATE -> createDebateRuntimeWithMemory(agentId, memory);
        };
    }

    public PipelineAgentRuntime createSequentialRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        SequentialPipeline pipeline = compositeFactory.createSequentialAgent(config, null);
        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    public PipelineAgentRuntime createSequentialRuntimeWithMemory(String agentId, Memory memory) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        SequentialPipeline pipeline = compositeFactory.createSequentialAgent(config, memory);
        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    public PipelineAgentRuntime createParallelRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        FanoutPipeline pipeline = compositeFactory.createParallelAgent(config, null);
        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    public PipelineAgentRuntime createParallelRuntimeWithMemory(String agentId, Memory memory) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        FanoutPipeline pipeline = compositeFactory.createParallelAgent(config, memory);
        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    public AgentRuntime createRoutingRuntime(String agentId) {
        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createRoutingAgent(
                configService.getAgentConfig(agentId), null, hook);
        return new AgentRuntime(agent, hook);
    }

    public AgentRuntime createRoutingRuntimeWithMemory(String agentId, Memory memory) {
        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createRoutingAgent(
                configService.getAgentConfig(agentId), memory, hook);
        return new AgentRuntime(agent, hook);
    }

    public AgentRuntime createHandoffsRuntime(String agentId) {
        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createHandoffsAgent(
                configService.getAgentConfig(agentId), null, hook);
        return new AgentRuntime(agent, hook);
    }

    public AgentRuntime createHandoffsRuntimeWithMemory(String agentId, Memory memory) {
        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createHandoffsAgent(
                configService.getAgentConfig(agentId), memory, hook);
        return new AgentRuntime(agent, hook);
    }

    public PipelineAgentRuntime createDebateRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        io.agentscope.core.pipeline.Pipeline<io.agentscope.core.message.Msg> pipeline =
                compositeFactory.createDebateAgent(config, null);
        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    public PipelineAgentRuntime createDebateRuntimeWithMemory(String agentId, Memory memory) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        io.agentscope.core.pipeline.Pipeline<io.agentscope.core.message.Msg> pipeline =
                compositeFactory.createDebateAgent(config, memory);
        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    public CompositeAgentFactory getCompositeFactory() {
        return compositeFactory;
    }

    public AgentConfigService getConfigService() {
        return configService;
    }

    private StreamingAgentRuntime createSingleRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        ApprovalHook approvalHook = createApprovalHookIfNeeded(config);

        ReActAgent agent = approvalHook != null
                ? compositeFactory.createSingleAgent(agentId, hook, approvalHook)
                : compositeFactory.createSingleAgent(agentId, hook);

        if (hasStructuredOutput(config)) {
            return new StructuredOutputAgentRuntime(agent, hook, config.getStructuredOutputClass());
        }
        return new AgentRuntime(agent, hook, approvalHook, approvalService, agentId);
    }

    private StreamingAgentRuntime createSingleRuntimeWithMemory(String agentId, Memory memory) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        ApprovalHook approvalHook = createApprovalHookIfNeeded(config);

        ReActAgent agent = approvalHook != null
                ? compositeFactory.createSingleAgentForSession(agentId, memory, hook, approvalHook)
                : compositeFactory.createSingleAgentForSession(agentId, memory, hook);

        if (hasStructuredOutput(config)) {
            return new StructuredOutputAgentRuntime(agent, hook, config.getStructuredOutputClass());
        }
        return new AgentRuntime(agent, hook, approvalHook, approvalService, agentId);
    }

    private boolean hasStructuredOutput(AgentConfig config) {
        return config.getStructuredOutputClass() != null && !config.getStructuredOutputClass().isBlank();
    }

    private ApprovalHook createApprovalHookIfNeeded(AgentConfig config) {
        boolean hasApproval = config.isApprovalRequired() ||
                (config.getApprovalTools() != null && !config.getApprovalTools().isEmpty());
        if (!hasApproval) {
            return null;
        }
        log.info("  ApprovalHook enabled for agent: {} (required={}, tools={})",
                config.getAgentId(), config.isApprovalRequired(), config.getApprovalTools());
        return new ApprovalHook(config.isApprovalRequired(), config.getApprovalTools());
    }
}

package com.skloda.agentscope.runtime;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentType;
import com.skloda.agentscope.composite.CompositeAgentFactory;
import com.skloda.agentscope.composite.graph.OrderFulfillmentGraph;
import com.skloda.agentscope.composite.pipeline.LoopPipeline;
import com.skloda.agentscope.composite.pipeline.RoundTablePipeline;
import com.skloda.agentscope.composite.pipeline.TaskOrchestratorPipeline;
import com.skloda.agentscope.composite.pipeline.TaskDispatcherPipeline;
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
            case LOOP -> createLoopRuntime(agentId);
            case STATE_GRAPH -> createStateGraphRuntime(agentId);
            case MSG_HUB -> createMsgHubRuntime(agentId);
            case SUBAGENT_SEQ -> createSubagentSeqRuntime(agentId);
            case SUBAGENT_PAR -> createSubagentParRuntime(agentId);
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
            case LOOP -> createLoopRuntimeWithMemory(agentId, memory);
            case STATE_GRAPH -> createStateGraphRuntimeWithMemory(agentId, memory);
            case MSG_HUB -> createMsgHubRuntimeWithMemory(agentId, memory);
            case SUBAGENT_SEQ -> createSubagentSeqRuntimeWithMemory(agentId, memory);
            case SUBAGENT_PAR -> createSubagentParRuntimeWithMemory(agentId, memory);
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

    public PipelineAgentRuntime createLoopRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        LoopPipeline pipeline = compositeFactory.createLoopAgent(config, null);
        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    public PipelineAgentRuntime createLoopRuntimeWithMemory(String agentId, Memory memory) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        LoopPipeline pipeline = compositeFactory.createLoopAgent(config, memory);
        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    public StateGraphRuntime createStateGraphRuntime(String agentId) {
        ObservabilityHook hook = new ObservabilityHook();
        OrderFulfillmentGraph graph = compositeFactory.createStateGraphAgent(
                configService.getAgentConfig(agentId), null);
        return new StateGraphRuntime(agentId, graph, hook);
    }

    public StateGraphRuntime createStateGraphRuntimeWithMemory(String agentId, Memory memory) {
        ObservabilityHook hook = new ObservabilityHook();
        OrderFulfillmentGraph graph = compositeFactory.createStateGraphAgent(
                configService.getAgentConfig(agentId), memory);
        return new StateGraphRuntime(agentId, graph, hook);
    }

    public MsgHubRuntime createMsgHubRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        RoundTablePipeline pipeline = compositeFactory.createMsgHubAgent(config, null);
        return new MsgHubRuntime(config.getAgentId(), pipeline, hook);
    }

    public MsgHubRuntime createMsgHubRuntimeWithMemory(String agentId, Memory memory) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        RoundTablePipeline pipeline = compositeFactory.createMsgHubAgent(config, memory);
        return new MsgHubRuntime(config.getAgentId(), pipeline, hook);
    }

    public PipelineAgentRuntime createSubagentSeqRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        TaskOrchestratorPipeline pipeline = compositeFactory.createSubagentSeqAgent(config, null);
        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    public PipelineAgentRuntime createSubagentSeqRuntimeWithMemory(String agentId, Memory memory) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        TaskOrchestratorPipeline pipeline = compositeFactory.createSubagentSeqAgent(config, memory);
        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    public PipelineAgentRuntime createSubagentParRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        TaskDispatcherPipeline pipeline = compositeFactory.createSubagentParAgent(config, null);
        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    public PipelineAgentRuntime createSubagentParRuntimeWithMemory(String agentId, Memory memory) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        TaskDispatcherPipeline pipeline = compositeFactory.createSubagentParAgent(config, memory);
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

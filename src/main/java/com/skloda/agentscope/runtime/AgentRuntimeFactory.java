package com.skloda.agentscope.runtime;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentType;
import com.skloda.agentscope.agent.MsgHubConfig;
import com.skloda.agentscope.agent.SubAgentConfig;
import com.skloda.agentscope.composite.CompositeAgentFactory;
import com.skloda.agentscope.composite.graph.OrderFulfillmentGraph;
import com.skloda.agentscope.hook.ApprovalHook;
import com.skloda.agentscope.hook.ObservabilityHook;
import com.skloda.agentscope.service.ApprovalService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.state.AgentStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
            case ROUTING -> createRoutingRuntime(agentId);
            case HANDOFFS -> createHandoffsRuntime(agentId);
            case STATE_GRAPH -> createStateGraphRuntime(agentId);
            case MSG_HUB -> createMsgHubRuntime(agentId);
            case HARNESS -> createHarnessRuntime(agentId);
            // Pipeline-dependent patterns disabled for 2.0 migration
            case SEQUENTIAL, PARALLEL, DEBATE, LOOP, SUBAGENT_SEQ, SUBAGENT_PAR ->
                throw new UnsupportedOperationException(
                    "Pattern " + type + " is disabled during AgentScope 2.0 migration (pipeline package removed). " +
                    "Will be reimplemented using 2.0 subagent/middleware API.");
        };
    }

    public StreamingAgentRuntime createRuntime(String agentId, String permissionMode) {
        log.debug("Creating AgentRuntime for agent: {} [permissionMode={}]", agentId, permissionMode);

        AgentConfig config = configService.getAgentConfig(agentId);
        AgentType type = config.getType() != null ? config.getType() : AgentType.SINGLE;

        return switch (type) {
            case SINGLE -> createSingleRuntimeWithPermission(agentId, permissionMode);
            case ROUTING -> createRoutingRuntime(agentId);
            case HANDOFFS -> createHandoffsRuntime(agentId);
            case STATE_GRAPH -> createStateGraphRuntime(agentId);
            case MSG_HUB -> createMsgHubRuntime(agentId);
            case HARNESS -> createHarnessRuntime(agentId);
            case SEQUENTIAL, PARALLEL, DEBATE, LOOP, SUBAGENT_SEQ, SUBAGENT_PAR ->
                throw new UnsupportedOperationException(
                    "Pattern " + type + " is disabled during AgentScope 2.0 migration");
        };
    }

    public StreamingAgentRuntime createRuntimeWithSession(String agentId, AgentStateStore stateStore) {
        log.debug("Creating AgentRuntime with shared session for agent: {}", agentId);

        AgentConfig config = configService.getAgentConfig(agentId);
        AgentType type = config.getType() != null ? config.getType() : AgentType.SINGLE;

        return switch (type) {
            case SINGLE -> createSingleRuntimeWithSession(agentId, stateStore);
            case ROUTING -> createRoutingRuntimeWithSession(agentId, stateStore);
            case HANDOFFS -> createHandoffsRuntimeWithSession(agentId, stateStore);
            case STATE_GRAPH -> createStateGraphRuntimeWithSession(agentId, stateStore);
            case MSG_HUB -> createMsgHubRuntimeWithSession(agentId, stateStore);
            case HARNESS -> createHarnessRuntimeWithSession(agentId, stateStore);
            // Pipeline-dependent patterns disabled for 2.0 migration
            case SEQUENTIAL, PARALLEL, DEBATE, LOOP, SUBAGENT_SEQ, SUBAGENT_PAR ->
                throw new UnsupportedOperationException(
                    "Pattern " + type + " is disabled during AgentScope 2.0 migration (pipeline package removed). " +
                    "Will be reimplemented using 2.0 subagent/middleware API.");
        };
    }

    public StreamingAgentRuntime createRuntimeWithSession(String agentId, AgentStateStore stateStore, String permissionMode) {
        log.debug("Creating AgentRuntime with session for agent: {} [permissionMode={}]", agentId, permissionMode);

        AgentConfig config = configService.getAgentConfig(agentId);
        AgentType type = config.getType() != null ? config.getType() : AgentType.SINGLE;

        return switch (type) {
            case SINGLE -> createSingleRuntimeWithSessionAndPermission(agentId, stateStore, permissionMode);
            case ROUTING -> createRoutingRuntimeWithSession(agentId, stateStore);
            case HANDOFFS -> createHandoffsRuntimeWithSession(agentId, stateStore);
            case STATE_GRAPH -> createStateGraphRuntimeWithSession(agentId, stateStore);
            case MSG_HUB -> createMsgHubRuntimeWithSession(agentId, stateStore);
            case HARNESS -> createHarnessRuntimeWithSession(agentId, stateStore);
            case SEQUENTIAL, PARALLEL, DEBATE, LOOP, SUBAGENT_SEQ, SUBAGENT_PAR ->
                throw new UnsupportedOperationException(
                    "Pattern " + type + " is disabled during AgentScope 2.0 migration");
        };
    }

    public AgentRuntime createRoutingRuntime(String agentId) {
        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createRoutingAgent(
                configService.getAgentConfig(agentId), (AgentStateStore) null);
        return new AgentRuntime(agent, hook);
    }

    public AgentRuntime createHandoffsRuntime(String agentId) {
        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createHandoffsAgent(
                configService.getAgentConfig(agentId), (AgentStateStore) null);
        return new AgentRuntime(agent, hook);
    }

    public StateGraphRuntime createStateGraphRuntime(String agentId) {
        ObservabilityHook hook = new ObservabilityHook();
        OrderFulfillmentGraph graph = compositeFactory.createStateGraphAgent(
                configService.getAgentConfig(agentId), (AgentStateStore) null);
        return new StateGraphRuntime(agentId, graph, hook);
    }

    public MsgHubRuntime createMsgHubRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();

        List<SubAgentConfig> subAgentConfigs = config.getSubAgents();
        if (subAgentConfigs == null || subAgentConfigs.size() < 2) {
            throw new IllegalArgumentException("MSG_HUB agent requires at least 2 sub-agents (experts + moderator). Got: " + subAgentConfigs);
        }

        // Last sub-agent is the moderator, all others are experts
        List<ReActAgent> experts = new ArrayList<>();
        for (int i = 0; i < subAgentConfigs.size() - 1; i++) {
            experts.add(compositeFactory.createSingleAgent(subAgentConfigs.get(i).getAgentId()));
        }
        ReActAgent moderator = compositeFactory.createSingleAgent(
                subAgentConfigs.get(subAgentConfigs.size() - 1).getAgentId());

        MsgHubConfig msgHubConfig = config.getMsgHubConfig();
        int rounds = msgHubConfig != null ? msgHubConfig.getRounds() : 3;

        return new MsgHubRuntime(experts, moderator, hook, agentId, rounds);
    }

    public StreamingAgentRuntime createHarnessRuntime(String agentId) {
        log.debug("HARNESS runtime not yet implemented, falling back to SINGLE for agent: {}", agentId);
        return createSingleRuntime(agentId);
    }

    // ---- Session-based runtime helpers (AgentScope 2.0) ----

    private StreamingAgentRuntime createSingleRuntimeWithSession(String agentId, AgentStateStore stateStore) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        ApprovalHook approvalHook = createApprovalHookIfNeeded(config);

        ReActAgent agent = compositeFactory.createSingleAgentForSession(agentId, stateStore, approvalHook);

        if (hasStructuredOutput(config)) {
            return new StructuredOutputAgentRuntime(agent, hook, config.getStructuredOutputClass());
        }
        return new AgentRuntime(agent, hook, approvalHook, approvalService, agentId);
    }

    private AgentRuntime createRoutingRuntimeWithSession(String agentId, AgentStateStore stateStore) {
        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createRoutingAgent(
                configService.getAgentConfig(agentId), stateStore);
        return new AgentRuntime(agent, hook);
    }

    private AgentRuntime createHandoffsRuntimeWithSession(String agentId, AgentStateStore stateStore) {
        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createHandoffsAgent(
                configService.getAgentConfig(agentId), stateStore);
        return new AgentRuntime(agent, hook);
    }

    private StateGraphRuntime createStateGraphRuntimeWithSession(String agentId, AgentStateStore stateStore) {
        ObservabilityHook hook = new ObservabilityHook();
        OrderFulfillmentGraph graph = compositeFactory.createStateGraphAgent(
                configService.getAgentConfig(agentId), stateStore);
        return new StateGraphRuntime(agentId, graph, hook);
    }

    private MsgHubRuntime createMsgHubRuntimeWithSession(String agentId, AgentStateStore stateStore) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();

        List<SubAgentConfig> subAgentConfigs = config.getSubAgents();
        if (subAgentConfigs == null || subAgentConfigs.size() < 2) {
            throw new IllegalArgumentException("MSG_HUB agent requires at least 2 sub-agents (experts + moderator). Got: " + subAgentConfigs);
        }

        // Last sub-agent is the moderator, all others are experts
        List<ReActAgent> experts = new ArrayList<>();
        for (int i = 0; i < subAgentConfigs.size() - 1; i++) {
            experts.add(compositeFactory.createSingleAgentForSession(
                    subAgentConfigs.get(i).getAgentId(), stateStore, null));
        }
        ReActAgent moderator = compositeFactory.createSingleAgentForSession(
                subAgentConfigs.get(subAgentConfigs.size() - 1).getAgentId(), stateStore, null);

        MsgHubConfig msgHubConfig = config.getMsgHubConfig();
        int rounds = msgHubConfig != null ? msgHubConfig.getRounds() : 3;

        return new MsgHubRuntime(experts, moderator, hook, agentId, rounds);
    }

    private StreamingAgentRuntime createHarnessRuntimeWithSession(String agentId, AgentStateStore stateStore) {
        log.debug("HARNESS runtime not yet implemented, falling back to SINGLE for agent: {}", agentId);
        return createSingleRuntimeWithSession(agentId, stateStore);
    }

    // ---- Permission-aware runtime helpers ----

    private StreamingAgentRuntime createSingleRuntimeWithPermission(String agentId, String permissionMode) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        ApprovalHook approvalHook = createApprovalHookIfNeeded(config);

        ReActAgent agent = compositeFactory.createSingleAgent(agentId, permissionMode, approvalHook);

        if (hasStructuredOutput(config)) {
            return new StructuredOutputAgentRuntime(agent, hook, config.getStructuredOutputClass());
        }
        return new AgentRuntime(agent, hook, approvalHook, approvalService, agentId);
    }

    private StreamingAgentRuntime createSingleRuntimeWithSessionAndPermission(String agentId, AgentStateStore stateStore, String permissionMode) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        ApprovalHook approvalHook = createApprovalHookIfNeeded(config);

        ReActAgent agent = compositeFactory.createSingleAgentForSession(agentId, stateStore, permissionMode, approvalHook);

        if (hasStructuredOutput(config)) {
            return new StructuredOutputAgentRuntime(agent, hook, config.getStructuredOutputClass());
        }
        return new AgentRuntime(agent, hook, approvalHook, approvalService, agentId);
    }

    // ---- Shared helpers ----

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

        ReActAgent agent = compositeFactory.createSingleAgent(agentId, approvalHook);

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

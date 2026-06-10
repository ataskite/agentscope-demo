package com.skloda.agentscope.runtime;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentType;
import com.skloda.agentscope.composite.CompositeAgentFactory;
import com.skloda.agentscope.composite.graph.OrderFulfillmentGraph;
import com.skloda.agentscope.hook.ApprovalHook;
import com.skloda.agentscope.hook.ObservabilityHook;
import com.skloda.agentscope.service.ApprovalService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.session.Session;
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
            case ROUTING -> createRoutingRuntime(agentId);
            case HANDOFFS -> createHandoffsRuntime(agentId);
            case STATE_GRAPH -> createStateGraphRuntime(agentId);
            case HARNESS -> createHarnessRuntime(agentId);
            // Pipeline-dependent patterns disabled for 2.0 migration
            case SEQUENTIAL, PARALLEL, DEBATE, LOOP, MSG_HUB, SUBAGENT_SEQ, SUBAGENT_PAR ->
                throw new UnsupportedOperationException(
                    "Pattern " + type + " is disabled during AgentScope 2.0 migration (pipeline package removed). " +
                    "Will be reimplemented using 2.0 subagent/middleware API.");
        };
    }

    /**
     * @deprecated Use {@link #createRuntimeWithSession(String, Session)} instead.
     *             Memory-based creation will be removed once 2.0 migration is complete.
     */
    @Deprecated
    public StreamingAgentRuntime createRuntimeWithMemory(String agentId, Memory memory) {
        log.debug("Creating AgentRuntime with shared memory for agent: {}", agentId);

        AgentConfig config = configService.getAgentConfig(agentId);
        AgentType type = config.getType() != null ? config.getType() : AgentType.SINGLE;

        return switch (type) {
            case SINGLE -> createSingleRuntimeWithMemory(agentId, memory);
            case ROUTING -> createRoutingRuntimeWithMemory(agentId, memory);
            case HANDOFFS -> createHandoffsRuntimeWithMemory(agentId, memory);
            case STATE_GRAPH -> createStateGraphRuntimeWithMemory(agentId, memory);
            case HARNESS -> createHarnessRuntimeWithMemory(agentId, memory);
            // Pipeline-dependent patterns disabled for 2.0 migration
            case SEQUENTIAL, PARALLEL, DEBATE, LOOP, MSG_HUB, SUBAGENT_SEQ, SUBAGENT_PAR ->
                throw new UnsupportedOperationException(
                    "Pattern " + type + " is disabled during AgentScope 2.0 migration (pipeline package removed). " +
                    "Will be reimplemented using 2.0 subagent/middleware API.");
        };
    }

    public StreamingAgentRuntime createRuntimeWithSession(String agentId, Session session) {
        log.debug("Creating AgentRuntime with shared session for agent: {}", agentId);

        AgentConfig config = configService.getAgentConfig(agentId);
        AgentType type = config.getType() != null ? config.getType() : AgentType.SINGLE;

        return switch (type) {
            case SINGLE -> createSingleRuntimeWithSession(agentId, session);
            case ROUTING -> createRoutingRuntimeWithSession(agentId, session);
            case HANDOFFS -> createHandoffsRuntimeWithSession(agentId, session);
            case STATE_GRAPH -> createStateGraphRuntimeWithSession(agentId, session);
            case HARNESS -> createHarnessRuntimeWithSession(agentId, session);
            // Pipeline-dependent patterns disabled for 2.0 migration
            case SEQUENTIAL, PARALLEL, DEBATE, LOOP, MSG_HUB, SUBAGENT_SEQ, SUBAGENT_PAR ->
                throw new UnsupportedOperationException(
                    "Pattern " + type + " is disabled during AgentScope 2.0 migration (pipeline package removed). " +
                    "Will be reimplemented using 2.0 subagent/middleware API.");
        };
    }

    public AgentRuntime createRoutingRuntime(String agentId) {
        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createRoutingAgent(
                configService.getAgentConfig(agentId), (Session) null, hook);
        return new AgentRuntime(agent, hook);
    }

    public AgentRuntime createRoutingRuntimeWithMemory(String agentId, Memory memory) {
        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createRoutingAgent(
                configService.getAgentConfig(agentId), (Session) null, hook);
        return new AgentRuntime(agent, hook);
    }

    public AgentRuntime createHandoffsRuntime(String agentId) {
        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createHandoffsAgent(
                configService.getAgentConfig(agentId), (Session) null, hook);
        return new AgentRuntime(agent, hook);
    }

    public AgentRuntime createHandoffsRuntimeWithMemory(String agentId, Memory memory) {
        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createHandoffsAgent(
                configService.getAgentConfig(agentId), (Session) null, hook);
        return new AgentRuntime(agent, hook);
    }

    public StateGraphRuntime createStateGraphRuntime(String agentId) {
        ObservabilityHook hook = new ObservabilityHook();
        OrderFulfillmentGraph graph = compositeFactory.createStateGraphAgent(
                configService.getAgentConfig(agentId), (Session) null);
        return new StateGraphRuntime(agentId, graph, hook);
    }

    public StateGraphRuntime createStateGraphRuntimeWithMemory(String agentId, Memory memory) {
        ObservabilityHook hook = new ObservabilityHook();
        OrderFulfillmentGraph graph = compositeFactory.createStateGraphAgent(
                configService.getAgentConfig(agentId), (Session) null);
        return new StateGraphRuntime(agentId, graph, hook);
    }

    public StreamingAgentRuntime createHarnessRuntime(String agentId) {
        log.debug("HARNESS runtime not yet implemented, falling back to SINGLE for agent: {}", agentId);
        return createSingleRuntime(agentId);
    }

    public StreamingAgentRuntime createHarnessRuntimeWithMemory(String agentId, Memory memory) {
        log.debug("HARNESS runtime not yet implemented, falling back to SINGLE for agent: {}", agentId);
        return createSingleRuntimeWithMemory(agentId, memory);
    }

    // ---- Session-based runtime helpers (AgentScope 2.0) ----

    private StreamingAgentRuntime createSingleRuntimeWithSession(String agentId, Session session) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        ApprovalHook approvalHook = createApprovalHookIfNeeded(config);

        ReActAgent agent = approvalHook != null
                ? compositeFactory.createSingleAgentForSession(agentId, session, hook, approvalHook)
                : compositeFactory.createSingleAgentForSession(agentId, session, hook);

        if (hasStructuredOutput(config)) {
            return new StructuredOutputAgentRuntime(agent, hook, config.getStructuredOutputClass());
        }
        return new AgentRuntime(agent, hook, approvalHook, approvalService, agentId);
    }

    private AgentRuntime createRoutingRuntimeWithSession(String agentId, Session session) {
        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createRoutingAgent(
                configService.getAgentConfig(agentId), session, hook);
        return new AgentRuntime(agent, hook);
    }

    private AgentRuntime createHandoffsRuntimeWithSession(String agentId, Session session) {
        ObservabilityHook hook = new ObservabilityHook();
        ReActAgent agent = compositeFactory.createHandoffsAgent(
                configService.getAgentConfig(agentId), session, hook);
        return new AgentRuntime(agent, hook);
    }

    private StateGraphRuntime createStateGraphRuntimeWithSession(String agentId, Session session) {
        ObservabilityHook hook = new ObservabilityHook();
        OrderFulfillmentGraph graph = compositeFactory.createStateGraphAgent(
                configService.getAgentConfig(agentId), session);
        return new StateGraphRuntime(agentId, graph, hook);
    }

    private StreamingAgentRuntime createHarnessRuntimeWithSession(String agentId, Session session) {
        log.debug("HARNESS runtime not yet implemented, falling back to SINGLE for agent: {}", agentId);
        return createSingleRuntimeWithSession(agentId, session);
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

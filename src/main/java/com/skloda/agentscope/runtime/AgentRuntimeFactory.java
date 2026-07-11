package com.skloda.agentscope.runtime;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentType;
import com.skloda.agentscope.agent.LoopConfig;
import com.skloda.agentscope.composite.CompositeAgentFactory;
import com.skloda.agentscope.composite.graph.OrderFulfillmentGraph;
import com.skloda.agentscope.middleware.ApprovalMiddleware;
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
    private final com.skloda.agentscope.harness.HarnessAgentFactory harnessAgentFactory;

    public AgentRuntimeFactory(CompositeAgentFactory compositeFactory,
                                AgentConfigService configService,
                                ApprovalService approvalService,
                                com.skloda.agentscope.harness.HarnessAgentFactory harnessAgentFactory) {
        this.compositeFactory = compositeFactory;
        this.configService = configService;
        this.approvalService = approvalService;
        this.harnessAgentFactory = harnessAgentFactory;
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
            case SEQUENTIAL -> createSequentialRuntime(agentId);
            case PARALLEL -> createParallelRuntime(agentId);
            case DEBATE -> createDebateRuntime(agentId);
            case LOOP -> createLoopRuntime(agentId);
            case SUBAGENT_SEQ -> createSubAgentSeqRuntime(agentId);
            case SUBAGENT_PAR -> createSubAgentParRuntime(agentId);
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
            case SEQUENTIAL -> createSequentialRuntime(agentId);
            case PARALLEL -> createParallelRuntime(agentId);
            case DEBATE -> createDebateRuntime(agentId);
            case LOOP -> createLoopRuntime(agentId);
            case SUBAGENT_SEQ -> createSubAgentSeqRuntime(agentId);
            case SUBAGENT_PAR -> createSubAgentParRuntime(agentId);
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
            case SEQUENTIAL -> createSequentialRuntimeWithSession(agentId, stateStore);
            case PARALLEL -> createParallelRuntimeWithSession(agentId, stateStore);
            case DEBATE -> createDebateRuntimeWithSession(agentId, stateStore);
            case LOOP -> createLoopRuntimeWithSession(agentId, stateStore);
            case SUBAGENT_SEQ -> createSubAgentSeqRuntimeWithSession(agentId, stateStore);
            case SUBAGENT_PAR -> createSubAgentParRuntimeWithSession(agentId, stateStore);
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
            case SEQUENTIAL -> createSequentialRuntimeWithSession(agentId, stateStore);
            case PARALLEL -> createParallelRuntimeWithSession(agentId, stateStore);
            case DEBATE -> createDebateRuntimeWithSession(agentId, stateStore);
            case LOOP -> createLoopRuntimeWithSession(agentId, stateStore);
            case SUBAGENT_SEQ -> createSubAgentSeqRuntimeWithSession(agentId, stateStore);
            case SUBAGENT_PAR -> createSubAgentParRuntimeWithSession(agentId, stateStore);
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
        // Delegate to CompositeAgentFactory (which uses createAgentForSession with a shared
        // AgentStateStore), consistent with the other 6 pipeline patterns. Previously this
        // was built inline with the stateless createSingleAgent, an inconsistency that left
        // MSG_HUB sub-agents without a shared session store.
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        return compositeFactory.createMsgHubRuntime(config, hook, null);
    }

    public StreamingAgentRuntime createHarnessRuntime(String agentId) {
        try {
            log.debug("Creating HarnessRuntime for agent: {}", agentId);
            io.agentscope.harness.agent.HarnessAgent harnessAgent =
                    harnessAgentFactory.create(
                            configService.getAgentConfig(agentId),
                            compositeFactory.getApiKey()
                    );
            io.agentscope.core.agent.RuntimeContext ctx = io.agentscope.core.agent.RuntimeContext.builder()
                    .sessionId("default")
                    .userId("demo-user")
                    .build();
            return new com.skloda.agentscope.harness.HarnessRuntime(harnessAgent, ctx);
        } catch (Exception e) {
            log.error("Failed to create HarnessRuntime for agent: {}", agentId, e);
            throw new RuntimeException("Failed to create HarnessRuntime", e);
        }
    }

    // ---- Session-based runtime helpers (AgentScope 2.0) ----

    private StreamingAgentRuntime createSingleRuntimeWithSession(String agentId, AgentStateStore stateStore) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        ApprovalMiddleware approvalMiddleware = createApprovalMiddlewareIfNeeded(config);

        ReActAgent agent = compositeFactory.createSingleAgentForSession(agentId, stateStore, approvalMiddleware);

        if (hasStructuredOutput(config)) {
            return new StructuredOutputAgentRuntime(agent, hook, config.getStructuredOutputClass());
        }
        return new AgentRuntime(agent, hook, approvalMiddleware, approvalService, agentId);
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
        // Delegate to CompositeAgentFactory (shared AgentStateStore), consistent with the
        // other 6 pipeline patterns and the no-session createMsgHubRuntime above.
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        return compositeFactory.createMsgHubRuntime(config, hook, stateStore);
    }

    private StreamingAgentRuntime createHarnessRuntimeWithSession(String agentId, AgentStateStore stateStore) {
        try {
            log.debug("Creating HarnessRuntime with session for agent: {}", agentId);
            io.agentscope.harness.agent.HarnessAgent harnessAgent =
                    harnessAgentFactory.create(
                            configService.getAgentConfig(agentId),
                            compositeFactory.getApiKey()
                    );
            io.agentscope.core.agent.RuntimeContext ctx = io.agentscope.core.agent.RuntimeContext.builder()
                    .sessionId("default")
                    .userId("demo-user")
                    .build();
            return new com.skloda.agentscope.harness.HarnessRuntime(harnessAgent, ctx);
        } catch (Exception e) {
            log.error("Failed to create HarnessRuntime with session for agent: {}", agentId, e);
            throw new RuntimeException("Failed to create HarnessRuntime", e);
        }
    }

    // ---- Permission-aware runtime helpers ----

    private StreamingAgentRuntime createSingleRuntimeWithPermission(String agentId, String permissionMode) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        ApprovalMiddleware approvalMiddleware = createApprovalMiddlewareIfNeeded(config);

        ReActAgent agent = compositeFactory.createSingleAgent(agentId, permissionMode, approvalMiddleware);

        if (hasStructuredOutput(config)) {
            return new StructuredOutputAgentRuntime(agent, hook, config.getStructuredOutputClass());
        }
        return new AgentRuntime(agent, hook, approvalMiddleware, approvalService, agentId);
    }

    private StreamingAgentRuntime createSingleRuntimeWithSessionAndPermission(String agentId, AgentStateStore stateStore, String permissionMode) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        ApprovalMiddleware approvalMiddleware = createApprovalMiddlewareIfNeeded(config);

        ReActAgent agent = compositeFactory.createSingleAgentForSession(agentId, stateStore, permissionMode, approvalMiddleware);

        if (hasStructuredOutput(config)) {
            return new StructuredOutputAgentRuntime(agent, hook, config.getStructuredOutputClass());
        }
        return new AgentRuntime(agent, hook, approvalMiddleware, approvalService, agentId);
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
        ApprovalMiddleware approvalMiddleware = createApprovalMiddlewareIfNeeded(config);

        ReActAgent agent = compositeFactory.createSingleAgent(agentId, approvalMiddleware);

        if (hasStructuredOutput(config)) {
            return new StructuredOutputAgentRuntime(agent, hook, config.getStructuredOutputClass());
        }
        return new AgentRuntime(agent, hook, approvalMiddleware, approvalService, agentId);
    }

    private boolean hasStructuredOutput(AgentConfig config) {
        return config.getStructuredOutputClass() != null && !config.getStructuredOutputClass().isBlank();
    }

    private ApprovalMiddleware createApprovalMiddlewareIfNeeded(AgentConfig config) {
        boolean hasApproval = config.isApprovalRequired() ||
                (config.getApprovalTools() != null && !config.getApprovalTools().isEmpty());
        if (!hasApproval) {
            return null;
        }
        log.info("  ApprovalMiddleware enabled for agent: {} (required={}, tools={})",
                config.getAgentId(), config.isApprovalRequired(), config.getApprovalTools());
        return new ApprovalMiddleware(config.isApprovalRequired(), config.getApprovalTools());
    }

    // ---- Pipeline runtime helpers (SEQUENTIAL, PARALLEL, DEBATE, LOOP, SUBAGENT_SEQ, SUBAGENT_PAR) ----

    private SequentialRuntime createSequentialRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        return compositeFactory.createSequentialRuntime(config, hook, null);
    }

    private ParallelRuntime createParallelRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        return compositeFactory.createParallelRuntime(config, hook, null);
    }

    private DebateRuntime createDebateRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        return compositeFactory.createDebateRuntime(config, hook, null);
    }

    private LoopRuntime createLoopRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        return compositeFactory.createLoopRuntime(config, hook, null);
    }

    private SubAgentSeqRuntime createSubAgentSeqRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        return compositeFactory.createSubAgentSeqRuntime(config, hook, null);
    }

    private SubAgentParRuntime createSubAgentParRuntime(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        return compositeFactory.createSubAgentParRuntime(config, hook, null);
    }

    // Session-based pipeline runtimes

    private SequentialRuntime createSequentialRuntimeWithSession(String agentId, AgentStateStore stateStore) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        return compositeFactory.createSequentialRuntime(config, hook, stateStore);
    }

    private ParallelRuntime createParallelRuntimeWithSession(String agentId, AgentStateStore stateStore) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        return compositeFactory.createParallelRuntime(config, hook, stateStore);
    }

    private DebateRuntime createDebateRuntimeWithSession(String agentId, AgentStateStore stateStore) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        return compositeFactory.createDebateRuntime(config, hook, stateStore);
    }

    private LoopRuntime createLoopRuntimeWithSession(String agentId, AgentStateStore stateStore) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        return compositeFactory.createLoopRuntime(config, hook, stateStore);
    }

    private SubAgentSeqRuntime createSubAgentSeqRuntimeWithSession(String agentId, AgentStateStore stateStore) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        return compositeFactory.createSubAgentSeqRuntime(config, hook, stateStore);
    }

    private SubAgentParRuntime createSubAgentParRuntimeWithSession(String agentId, AgentStateStore stateStore) {
        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        return compositeFactory.createSubAgentParRuntime(config, hook, stateStore);
    }
}

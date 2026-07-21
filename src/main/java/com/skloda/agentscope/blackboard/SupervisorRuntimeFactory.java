package com.skloda.agentscope.blackboard;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentFactory;
import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Spring component that builds {@link SupervisorRuntime} instances.
 *
 * <p>Called by {@link com.skloda.agentscope.runtime.AgentRuntimeFactory} ONLY when a ROUTING
 * agent declares {@code sharedBlackboard.enabled: true}. Otherwise the legacy
 * {@code createRoutingAgent} path is used and this factory is never invoked.
 *
 * <p>The Supervisor's own Conversation AgentState lives in a store resolved by the caller
 * (typically the SessionManager-supplied store, so it persists across requests). Each
 * expert gets a fresh {@link InMemoryAgentStateStore} via {@link ExpertAgentProvider} —
 * those are intentionally not persisted.
 */
@Component
public class SupervisorRuntimeFactory {

    private static final Logger log = LoggerFactory.getLogger(SupervisorRuntimeFactory.class);

    private final AgentConfigService configService;
    private final AgentFactory agentFactory;
    private final BlackboardService blackboardService;
    private final RoutingDecisionService routingService;

    public SupervisorRuntimeFactory(AgentConfigService configService,
                                    AgentFactory agentFactory,
                                    BlackboardService blackboardService,
                                    RoutingDecisionService routingService) {
        this.configService = configService;
        this.agentFactory = agentFactory;
        this.blackboardService = blackboardService;
        this.routingService = routingService;
    }

    /**
     * Build a Supervisor runtime for one request.
     *
     * @param agentId    the router agentId (must have sharedBlackboard.enabled=true)
     * @param stateStore the Supervisor's Conversation state store (shared across requests
     *                   for the same session via SessionManagerService); if null, a fresh
     *                   in-memory store is used
     * @param userId     real user id (may be null for anonymous)
     * @param sessionId  real session id (must be non-null for routing to work)
     */
    public SupervisorRuntime create(String agentId,
                                    AgentStateStore stateStore,
                                    String userId,
                                    String sessionId) {
        Objects.requireNonNull(agentId, "agentId");
        AgentConfig cfg = configService.getAgentConfig(agentId);
        if (cfg == null) {
            throw new IllegalArgumentException("Agent not found: " + agentId);
        }
        AgentConfig.SharedBlackboardConfig bb = cfg.getSharedBlackboard();
        if (bb == null || !bb.isEnabled()) {
            throw new IllegalStateException(
                    "Agent " + agentId + " does not have sharedBlackboard.enabled=true");
        }

        // Supervisor Conversation store: caller-supplied (session-scoped) or fresh.
        AgentStateStore supervisorStore = stateStore != null
                ? stateStore
                : new InMemoryAgentStateStore();

        // Build (or rebuild) the Supervisor's own ReActAgent from YAML config.
        ReActAgent supervisorAgent = agentFactory.createAgentForSession(agentId, supervisorStore);

        // Expert provider: wires AgentFactory which resolves expert YAML configs.
        ExpertAgentProvider expertProvider = new ExpertAgentProvider(agentFactory, configService);

        ObservabilityHook hook = new ObservabilityHook();

        log.debug("Built SupervisorRuntime for agent={} user={} session={} store={}",
                agentId, userId, sessionId, supervisorStore.getClass().getSimpleName());

        return new SupervisorRuntime(cfg, supervisorAgent, hook, blackboardService,
                routingService, expertProvider, userId, sessionId);
    }

    /** Convenience: detect whether a given agent is a Supervisor-enabled router. */
    public static boolean isSupervisor(AgentConfig cfg) {
        return cfg != null
                && cfg.getSharedBlackboard() != null
                && cfg.getSharedBlackboard().isEnabled();
    }
}

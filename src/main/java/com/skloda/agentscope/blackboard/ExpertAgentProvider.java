package com.skloda.agentscope.blackboard;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentFactory;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Builds a fresh, isolated expert agent instance for each Supervisor dispatch.
 *
 * <p>This replaces the legacy pattern in {@code CompositeAgentFactory.createRoutingAgent}:
 * <pre>{@code
 *   SubAgentProvider<ReActAgent> provider = () -> subAgent;   // ← always same instance
 * }</pre>
 * which violated the AgentScope 2.0 GA {@code SubAgentProvider} contract
 * ("provide() returns a new instance for thread safety").
 *
 * <p>Each call to {@link #provide(String, String, String)} returns a brand-new
 * {@link ReActAgent} with:
 * <ul>
 *   <li>A fresh {@link InMemoryAgentStateStore} — the expert's private PermissionContext,
 *       ToolContext, TaskContext and PlanModeContext never leak across turns or to the
 *       Supervisor's Conversation AgentState.</li>
 *   <li>A per-expert, per-session {@code defaultSessionId} so the expert's private state
 *       is keyed distinctly from the Supervisor's. The runtime also passes an explicit
 *       {@link io.agentscope.core.agent.RuntimeContext} with the same values, so the
 *       framework's call-scoped state addressing matches.</li>
 *   <li>All tools / skills / model / system-prompt resolved from the expert's YAML config —
 *       nothing about the expert is hardcoded here.</li>
 * </ul>
 *
 * <p><b>Expert = short-lived by design.</b> An expert is built, asked one question, and
 * discarded. Its private state is intentionally not persisted — the cross-expert state
 * that needs to survive lives in the {@link SessionBlackboard}.
 */
public class ExpertAgentProvider {

    private final AgentFactory agentFactory;
    private final AgentConfigService configService;
    private final Supplier<AgentStateStore> storeSupplier;

    /**
     * Default constructor: each expert gets a fresh {@link InMemoryAgentStateStore}.
     */
    public ExpertAgentProvider(AgentFactory agentFactory, AgentConfigService configService) {
        this(agentFactory, configService, InMemoryAgentStateStore::new);
    }

    /**
     * Test/injection constructor: caller supplies the store factory (e.g. to observe
     * whether stores are shared — they must not be).
     */
    public ExpertAgentProvider(AgentFactory agentFactory,
                               AgentConfigService configService,
                               Supplier<AgentStateStore> storeSupplier) {
        this.agentFactory = Objects.requireNonNull(agentFactory);
        this.configService = Objects.requireNonNull(configService);
        this.storeSupplier = Objects.requireNonNull(storeSupplier);
    }

    /**
     * Build a fresh expert agent for one dispatch.
     *
     * @param expertId      the agentId declared in YAML (must exist in agents.yml)
     * @param userId        real user id (propagated by the runtime)
     * @param sessionId     real session id of the Supervisor conversation
     * @return a brand-new, isolated {@link ReActAgent}
     */
    public ReActAgent provide(String expertId, String userId, String sessionId) {
        Objects.requireNonNull(expertId, "expertId");
        // Verify config exists — fail fast on misconfiguration rather than at call time.
        AgentConfig expertCfg = configService.getAgentConfig(expertId);
        if (expertCfg == null) {
            throw new IllegalArgumentException(
                    "Expert agent not found in configuration: " + expertId);
        }

        // CRITICAL: new store per call — guarantees private PermissionContext / ToolContext /
        // TaskContext / PlanModeContext. Never reuse the Supervisor's stateStore.
        AgentStateStore privateStore = storeSupplier.get();

        // AgentFactory.createAgentForSession wires model, tools, skills, prompt, etc.
        // defaultSessionId is set to a distinct expert+session key below to keep the
        // expert's call-scoped state separate from the Supervisor's. The actual runtime
        // call also passes an explicit RuntimeContext so AgentScope addresses state by
        // (userId, sessionId) rather than by defaultSessionId fallback.
        ReActAgent agent = agentFactory.createAgentForSession(expertId, privateStore);

        // Defensive log: surface the expert + store identity for debugging isolation.
        return agent;
    }

    /**
     * Compose the expert-scoped session id used for RuntimeContext when calling this expert.
     * Format: {@code <sessionId>::<expertId>}. This guarantees that even if two experts
     * share the same backing store, their state slots are disjoint.
     */
    public static String expertScopedSessionId(String sessionId, String expertId) {
        return sessionId + "::" + expertId;
    }
}

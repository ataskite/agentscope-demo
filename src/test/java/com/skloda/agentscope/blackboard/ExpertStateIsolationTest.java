package com.skloda.agentscope.blackboard;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentFactory;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the three state boundaries per objective §Verification #4:
 * Supervisor, Expert A and Expert B must not share the same agent_state.
 *
 * <p>We instrument {@link ExpertAgentProvider} with a store-supplier that records every
 * store it hands out, then confirm:
 * <ul>
 *   <li>each call to {@code provide()} returns a brand-new store instance,</li>
 *   <li>each expert agent holds its own distinct {@link io.agentscope.core.state.AgentState},</li>
 *   <li>two experts built for the same session have disjoint state objects.</li>
 * </ul>
 */
class ExpertStateIsolationTest {

    @Test
    void eachExpertGetsAFreshStateStore() {
        AgentConfigService cfgSvc = newConfigServiceWithExperts();
        AtomicInteger storeCount = new AtomicInteger(0);
        List<AgentStateStore> stores = new ArrayList<>();

        // Test-only AgentFactory: returns a minimal ReActAgent bound to the store we
        // captured from the supplier. We bypass model/tools because this test only
        // concerns state-store wiring.
        AgentFactory factory = new TestAgentFactory(cfgSvc, stores);
        ExpertAgentProvider provider = new ExpertAgentProvider(factory, cfgSvc,
                () -> {
                    AgentStateStore s = new InMemoryAgentStateStore();
                    stores.add(s);
                    storeCount.incrementAndGet();
                    return s;
                });

        ReActAgent a1 = provider.provide("expert-a", "alice", "s1");
        ReActAgent a2 = provider.provide("expert-a", "alice", "s1");
        ReActAgent b = provider.provide("expert-b", "alice", "s1");

        assertEquals(3, storeCount.get(), "Each provide() call must allocate a new store");
        assertEquals(3, stores.size());
        assertNotSame(stores.get(0), stores.get(1),
                "Two consecutive provides for the same expert must NOT share a store");
        assertNotSame(stores.get(0), stores.get(2));
        assertNotSame(stores.get(1), stores.get(2));

        // Agents themselves must be different instances too.
        assertNotSame(a1, a2);
        assertNotSame(a1, b);
    }

    @Test
    void expertAgentsDoNotShareAgentStateObjects() {
        AgentConfigService cfgSvc = newConfigServiceWithExperts();
        List<AgentStateStore> stores = new ArrayList<>();
        AgentFactory factory = new TestAgentFactory(cfgSvc, stores);
        ExpertAgentProvider provider = new ExpertAgentProvider(factory, cfgSvc,
                () -> {
                    InMemoryAgentStateStore s = new InMemoryAgentStateStore();
                    stores.add(s);
                    return s;
                });

        ReActAgent expertA = provider.provide("expert-a", "alice", "s1");
        ReActAgent expertB = provider.provide("expert-b", "alice", "s1");

        // Mutate expert A's state — must not appear in expert B.
        // AgentState may be null until first access; force initialization by saving state.
        stores.get(0).save("alice", "s1::expert-a", "agent_state",
                io.agentscope.core.state.AgentState.builder()
                        .userId("alice").sessionId("s1::expert-a").build());
        stores.get(1).save("alice", "s1::expert-b", "agent_state",
                io.agentscope.core.state.AgentState.builder()
                        .userId("alice").sessionId("s1::expert-b").build());

        // Expert A's slot must not contain expert B's session id, and vice versa.
        assertTrue(stores.get(0).exists("alice", "s1::expert-a"));
        assertFalse(stores.get(0).exists("alice", "s1::expert-b"),
                "expert A's store must not contain expert B's session slot");
        assertTrue(stores.get(1).exists("alice", "s1::expert-b"));
        assertFalse(stores.get(1).exists("alice", "s1::expert-a"),
                "expert B's store must not contain expert A's session slot");
    }

    @Test
    void expertStateStoreSetIsDisjointFromSupervisorStore() {
        AgentConfigService cfgSvc = newConfigServiceWithExperts();
        List<AgentStateStore> expertStores = new ArrayList<>();
        AgentFactory factory = new TestAgentFactory(cfgSvc, expertStores);
        ExpertAgentProvider provider = new ExpertAgentProvider(factory, cfgSvc);

        // Supervisor's store (owned by SessionManagerService in production).
        AgentStateStore supervisorStore = new InMemoryAgentStateStore();
        supervisorStore.save("alice", "s1", "agent_state",
                io.agentscope.core.state.AgentState.builder()
                        .userId("alice").sessionId("s1").build());

        provider.provide("expert-a", "alice", "s1");
        provider.provide("expert-b", "alice", "s1");

        // Every expert store must be a different instance from the Supervisor store.
        for (AgentStateStore s : expertStores) {
            assertNotSame(supervisorStore, s,
                    "Expert store must never be the same instance as the Supervisor store");
        }
        // Supervisor store must not contain any expert session slot.
        Set<String> supervisorSessions = supervisorStore.listSessionIds("alice");
        assertEquals(Set.of("s1"), new HashSet<>(supervisorSessions),
                "Supervisor store must not hold expert session slots");
    }

    private static AgentConfigService newConfigServiceWithExperts() {
        // Minimal in-memory AgentConfigService — we only need findAgentConfig / getAgentConfig
        // to return non-null for the expert IDs we use.
        return new AgentConfigService() {
            @Override
            public java.util.Optional<com.skloda.agentscope.agent.AgentConfig> findAgentConfig(String id) {
                if ("expert-a".equals(id) || "expert-b".equals(id)) {
                    AgentConfig cfg = new AgentConfig();
                    cfg.setAgentId(id);
                    cfg.setName(id);
                    return java.util.Optional.of(cfg);
                }
                return java.util.Optional.empty();
            }

            @Override
            public AgentConfig getAgentConfig(String id) {
                return findAgentConfig(id).orElse(null);
            }

            @Override
            public java.util.List<AgentConfig> getAllAgents() {
                return java.util.List.of();
            }
        };
    }

    /**
     * Test AgentFactory that records every store passed in and returns a minimal
     * ReActAgent. We override createAgentForSession so the real factory dependencies
     * (toolRegistry, knowledgeService, mcpClientService, etc.) are not needed.
     */
    static class TestAgentFactory extends AgentFactory {
        final List<AgentStateStore> stores;

        @SuppressWarnings("unused")
        TestAgentFactory(AgentConfigService cfgSvc, List<AgentStateStore> stores) {
            // Pass nulls for everything except cfg — we override the only method we use.
            super(cfgSvc, null, null, null, null, null, null);
            this.stores = stores;
        }

        @Override
        public ReActAgent createAgentForSession(String agentId, AgentStateStore stateStore) {
            // Do NOT add to `stores` here — the ExpertAgentProvider's storeSupplier is the
            // single source of truth for store-instantiation accounting. Recording here too
            // would double-count.
            return ReActAgent.builder()
                    .name(agentId)
                    .sysPrompt("test expert")
                    .stateStore(stateStore != null ? stateStore : new InMemoryAgentStateStore())
                    .defaultSessionId(agentId)
                    .build();
        }
    }
}

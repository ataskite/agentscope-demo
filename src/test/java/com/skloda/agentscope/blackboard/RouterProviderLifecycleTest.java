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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the SubAgentProvider contract per objective §Verification #6:
 * Provider must create new instances per dispatch — no cross-session state leakage.
 *
 * <p>The legacy code used {@code SubAgentProvider<ReActAgent> provider = () -> subAgent;}
 * which returned the SAME instance forever. {@link ExpertAgentProvider} replaces this with
 * a factory pattern that builds a fresh {@link ReActAgent} + fresh {@link AgentStateStore}
 * on every call. These tests verify that invariant including under concurrency.
 */
class RouterProviderLifecycleTest {

    @Test
    void provideReturnsNewInstanceEveryCall() {
        AgentConfigService cfgSvc = fakeConfigService();
        AgentFactory factory = new StubAgentFactory();
        ExpertAgentProvider provider = new ExpertAgentProvider(factory, cfgSvc);

        Set<ReActAgent> instances = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            ReActAgent a = provider.provide("expert-a", "alice", "s1");
            assertNotNull(a);
            instances.add(a);
        }
        assertEquals(10, instances.size(),
                "Each provide() must return a distinct ReActAgent instance");
    }

    @Test
    void storesAreNeverSharedAcrossSessionsOrExperts() {
        AgentConfigService cfgSvc = fakeConfigService();
        List<AgentStateStore> createdStores = new ArrayList<>();
        AgentFactory factory = new StubAgentFactory() {
            @Override
            public ReActAgent createAgentForSession(String agentId, AgentStateStore store) {
                createdStores.add(store);
                return super.createAgentForSession(agentId, store);
            }
        };
        ExpertAgentProvider provider = new ExpertAgentProvider(factory, cfgSvc);

        provider.provide("expert-a", "alice", "s1");
        provider.provide("expert-a", "alice", "s2");
        provider.provide("expert-b", "alice", "s1");
        provider.provide("expert-a", "bob", "s1");

        assertEquals(4, createdStores.size());
        assertEquals(4, new HashSet<>(createdStores).size(),
                "All four dispatches must use distinct store instances");
    }

    @Test
    void concurrentProvidesDoNotShareState() throws Exception {
        AgentConfigService cfgSvc = fakeConfigService();
        AgentFactory factory = new StubAgentFactory();
        ExpertAgentProvider provider = new ExpertAgentProvider(factory, cfgSvc);

        int threads = 16;
        int perThread = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        Set<ReActAgent> allInstances = java.util.Collections.synchronizedSet(new HashSet<>());

        try {
            for (int t = 0; t < threads; t++) {
                final String sid = "s" + t;
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            ReActAgent a = provider.provide("expert-a", "u" + sid, sid);
                            allInstances.add(a);
                        }
                    } catch (Throwable e) {
                        failures.incrementAndGet();
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS),
                    "Concurrent provider calls must complete within timeout");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(0, failures.get(), "No provider failures expected under concurrency");
        assertEquals(threads * perThread, allInstances.size(),
                "Every concurrent dispatch must produce a unique instance");
    }

    @Test
    void expertScopedSessionIdIsDistinctFromSupervisorSession() {
        String expertSid = ExpertAgentProvider.expertScopedSessionId("s1", "expert-a");
        assertNotEquals("s1", expertSid);
        assertTrue(expertSid.endsWith("::expert-a"));
        // Two different experts get different scoped ids
        assertNotEquals(
                ExpertAgentProvider.expertScopedSessionId("s1", "expert-a"),
                ExpertAgentProvider.expertScopedSessionId("s1", "expert-b"));
    }

    private static AgentConfigService fakeConfigService() {
        return new AgentConfigService() {
            @Override
            public AgentConfig getAgentConfig(String id) {
                AgentConfig cfg = new AgentConfig();
                cfg.setAgentId(id);
                cfg.setName(id);
                return cfg;
            }

            @Override
            public java.util.Optional<AgentConfig> findAgentConfig(String id) {
                return java.util.Optional.of(getAgentConfig(id));
            }

            @Override
            public List<AgentConfig> getAllAgents() { return List.of(); }
        };
    }

    /** Minimal AgentFactory that bypasses real dependencies. */
    static class StubAgentFactory extends AgentFactory {
        @SuppressWarnings("unused")
        StubAgentFactory() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public ReActAgent createAgentForSession(String agentId, AgentStateStore stateStore) {
            return ReActAgent.builder()
                    .name(agentId)
                    .sysPrompt("stub")
                    .stateStore(stateStore != null ? stateStore : new InMemoryAgentStateStore())
                    .defaultSessionId(agentId)
                    .build();
        }
    }
}

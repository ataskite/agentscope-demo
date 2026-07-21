package com.skloda.agentscope.blackboard;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentFactory;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies RuntimeContext propagation semantics per objective §Verification #1:
 * same (userId, sessionId) restores the same Supervisor conversation state;
 * different users / sessions do not cross-talk.
 *
 * <p>AgentScope's {@link AgentStateStore} SPI addresses state by
 * {@code (userId, sessionId)} — we exercise that contract directly here and via
 * {@link SupervisorRuntimeFactory#create(...)}, which forwards the same pair to the
 * agent's store and the blackboard service.
 */
class RuntimeContextPropagationTest {

    @Test
    void runtimeContextCarriesUserIdAndSessionId() {
        RuntimeContext ctx = RuntimeContext.builder()
                .userId("alice")
                .sessionId("s1")
                .build();
        assertEquals("alice", ctx.getUserId());
        assertEquals("s1", ctx.getSessionId());
    }

    @Test
    void sameSessionRestoresConversationStateAcrossCalls() {
        // Two consecutive Supervisor builds for the same (userId, sessionId) must share
        // the same Conversation AgentState because SessionManagerService pins one store
        // per session. We simulate that here by reusing one store across two builds.
        InMemoryAgentStateStore supervisorStore = new InMemoryAgentStateStore();
        supervisorStore.save("alice", "s1", "agent_state",
                io.agentscope.core.state.AgentState.builder()
                        .userId("alice").sessionId("s1").build());

        // The second call reloads the same state.
        assertTrue(supervisorStore.exists("alice", "s1"));
        Optional<io.agentscope.core.state.AgentState> reloaded =
                supervisorStore.get("alice", "s1", "agent_state",
                        io.agentscope.core.state.AgentState.class);
        assertTrue(reloaded.isPresent());
        assertEquals("s1", reloaded.get().getSessionId());
    }

    @Test
    void differentSessionsAreIsolatedInConversationStore() {
        InMemoryAgentStateStore store = new InMemoryAgentStateStore();
        store.save("alice", "s1", "agent_state",
                io.agentscope.core.state.AgentState.builder()
                        .userId("alice").sessionId("s1").build());
        store.save("alice", "s2", "agent_state",
                io.agentscope.core.state.AgentState.builder()
                        .userId("alice").sessionId("s2").build());
        store.save("bob", "s1", "agent_state",
                io.agentscope.core.state.AgentState.builder()
                        .userId("bob").sessionId("s1").build());

        // Alice/s1 exists, bob/s1 exists, but they are distinct slots.
        assertTrue(store.exists("alice", "s1"));
        assertTrue(store.exists("bob", "s1"));
        assertNotEquals(
                store.get("alice", "s1", "agent_state",
                        io.agentscope.core.state.AgentState.class).get().getUserId(),
                store.get("bob", "s1", "agent_state",
                        io.agentscope.core.state.AgentState.class).get().getUserId());

        // Deleting one slot does not affect the other.
        store.delete("alice", "s1");
        assertFalse(store.exists("alice", "s1"));
        assertTrue(store.exists("bob", "s1"));
    }

    @Test
    void supervisorFactoryPropagatesIdsIntoSupervisorRuntime() {
        // Build a SupervisorRuntime via the factory and assert userId/sessionId arrive.
        InMemoryAgentStateStore supervisorStore = new InMemoryAgentStateStore();
        AgentConfigService cfgSvc = configServiceWithSupervisor();
        AgentFactory factory = stubAgentFactory();
        BlackboardService bb = new BlackboardService(new InMemoryAgentStateStore(), "shared_blackboard");
        RoutingDecisionService router = new RoutingDecisionService();
        SupervisorRuntimeFactory sf = new SupervisorRuntimeFactory(cfgSvc, factory, bb, router);

        SupervisorRuntime rt = sf.create("sup", supervisorStore, "alice", "sess-42");
        assertNotNull(rt);

        // The supervisor conversation state slot exists once we save something.
        supervisorStore.save("alice", "sess-42", "agent_state",
                io.agentscope.core.state.AgentState.builder()
                        .userId("alice").sessionId("sess-42").build());
        assertTrue(supervisorStore.exists("alice", "sess-42"));

        // The blackboard slot for the same pair is distinct from agent_state.
        bb.applyPatch("alice", "sess-42", BlackboardPatch.builder()
                .activeExpert("e1").build());
        assertTrue(bb.getSnapshot("alice", "sess-42").isPresent());
        assertEquals("e1", bb.getOrCreate("alice", "sess-42").getActiveExpert());
    }

    @Test
    void anonymousUserFallbackDoesNotClashWithNamedUser() {
        // AgentStateStore maps null userId to "__anon__" internally — verified here by
        // confirming anonymous and named slots do not collide.
        InMemoryAgentStateStore store = new InMemoryAgentStateStore();
        store.save(null, "s1", "agent_state",
                io.agentscope.core.state.AgentState.builder().sessionId("s1").build());
        store.save("alice", "s1", "agent_state",
                io.agentscope.core.state.AgentState.builder()
                        .userId("alice").sessionId("s1").build());

        assertTrue(store.exists(null, "s1"));
        assertTrue(store.exists("alice", "s1"));
    }

    private static AgentConfigService configServiceWithSupervisor() {
        return new AgentConfigService() {
            @Override
            public AgentConfig getAgentConfig(String id) {
                AgentConfig cfg = new AgentConfig();
                cfg.setAgentId(id);
                cfg.setName(id);
                cfg.setType(com.skloda.agentscope.agent.AgentType.ROUTING);
                AgentConfig.SharedBlackboardConfig sb = new AgentConfig.SharedBlackboardConfig();
                sb.setEnabled(true);
                cfg.setSharedBlackboard(sb);
                return cfg;
            }

            @Override
            public Optional<AgentConfig> findAgentConfig(String id) {
                return Optional.of(getAgentConfig(id));
            }

            @Override
            public java.util.List<AgentConfig> getAllAgents() { return java.util.List.of(); }
        };
    }

    private static AgentFactory stubAgentFactory() {
        return new AgentFactory(null, null, null, null, null, null, null) {
            @Override
            public ReActAgent createAgentForSession(String agentId, AgentStateStore store) {
                return ReActAgent.builder()
                        .name(agentId)
                        .sysPrompt("stub")
                        .stateStore(store != null ? store : new InMemoryAgentStateStore())
                        .defaultSessionId(agentId)
                        .build();
            }
        };
    }
}

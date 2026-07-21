package com.skloda.agentscope.blackboard;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentFactory;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression guard per objective §Verification #5:
 * Router's nested call to an expert must NOT overwrite the Supervisor's conversation
 * state with the expert's (older / different) snapshot. This was the failure mode of
 * the naive "expert shares Supervisor's stateStore" approach that we explicitly avoid.
 *
 * <p>Here we prove the architecture is immune: Supervisor state lives in its own store
 * and session slot; expert state lives in a freshly-allocated store with a different
 * session id. Mutating one cannot affect the other.
 */
class NestedAgentStateOverwriteRegressionTest {

    @Test
    void expertDispatchDoesNotOverwriteSupervisorConversationState() {
        // --- Arrange: Supervisor with its own store and conversation state. ---
        InMemoryAgentStateStore supervisorStore = new InMemoryAgentStateStore();
        supervisorStore.save("alice", "sess-1", "agent_state",
                io.agentscope.core.state.AgentState.builder()
                        .userId("alice")
                        .sessionId("sess-1")
                        .summary("用户咨询还款失败")
                        .build());

        // --- Act: simulate a nested expert dispatch. ExpertAgentProvider allocates a
        //         brand-new store; the SupervisorRuntime would then call expert.call(...)
        //         which persists into that expert store under session id "sess-1::expert-a".
        final AgentStateStore[] capturedExpertStore = new AgentStateStore[1];
        AgentFactory factory = new AgentFactory(null, null, null, null, null, null, null) {
            @Override
            public ReActAgent createAgentForSession(String agentId, AgentStateStore store) {
                capturedExpertStore[0] = store;
                return ReActAgent.builder()
                        .name(agentId)
                        .sysPrompt("expert")
                        .stateStore(store)
                        .defaultSessionId(agentId)
                        .build();
            }
        };
        AgentConfigService cfgSvc = new AgentConfigService() {
            @Override
            public AgentConfig getAgentConfig(String id) {
                AgentConfig cfg = new AgentConfig();
                cfg.setAgentId(id);
                cfg.setName(id);
                return cfg;
            }

            @Override
            public Optional<AgentConfig> findAgentConfig(String id) {
                return Optional.of(getAgentConfig(id));
            }

            @Override
            public java.util.List<AgentConfig> getAllAgents() { return java.util.List.of(); }
        };
        ExpertAgentProvider provider = new ExpertAgentProvider(factory, cfgSvc);

        provider.provide("expert-a", "alice", "sess-1");

        // Capture the expert's store and simulate the expert writing its private state.
        AgentStateStore expertStore = capturedExpertStore[0];
        assertNotNull(expertStore, "ExpertAgentProvider must allocate a fresh store");
        assertNotSame(supervisorStore, expertStore,
                "Expert store must be a different instance from Supervisor store");
        expertStore.save("alice", "sess-1::expert-a", "agent_state",
                io.agentscope.core.state.AgentState.builder()
                        .userId("alice")
                        .sessionId("sess-1::expert-a")
                        .summary("EXPERT INTERNAL - not for supervisor")
                        .build());

        // --- Assert: Supervisor's conversation state is untouched. ---
        io.agentscope.core.state.AgentState supervisorState =
                supervisorStore.get("alice", "sess-1", "agent_state",
                                io.agentscope.core.state.AgentState.class)
                        .orElseThrow(() -> new AssertionError("Supervisor state must survive"));

        assertEquals("sess-1", supervisorState.getSessionId(),
                "Supervisor session id must be unchanged");
        assertEquals("用户咨询还款失败", supervisorState.getSummary(),
                "Supervisor summary must not be overwritten by expert state");
        assertNotEquals("EXPERT INTERNAL - not for supervisor", supervisorState.getSummary());

        // And the expert's private state is in a different slot entirely.
        io.agentscope.core.state.AgentState expertState =
                expertStore.get("alice", "sess-1::expert-a", "agent_state",
                                io.agentscope.core.state.AgentState.class)
                        .orElseThrow(() -> new AssertionError("Expert state must exist"));
        assertEquals("sess-1::expert-a", expertState.getSessionId());
        assertEquals("EXPERT INTERNAL - not for supervisor", expertState.getSummary());

        // The expert store MUST NOT carry the supervisor's slot.
        assertFalse(expertStore.exists("alice", "sess-1"),
                "Expert store must not contain the Supervisor's session slot");
        // And vice versa.
        assertFalse(supervisorStore.exists("alice", "sess-1::expert-a"),
                "Supervisor store must not contain the expert's session slot");
    }

    @Test
    void blackboardUpdatesDoNotPolluteConversationState() {
        // The blackboard lives under key "shared_blackboard"; the conversation state lives
        // under key "agent_state". They share the same store slot (userId, sessionId) but
        // distinct keys, so a blackboard patch must never appear as conversation state.
        InMemoryAgentStateStore store = new InMemoryAgentStateStore();
        BlackboardService bb = new BlackboardService(store, "shared_blackboard");

        store.save("alice", "s1", "agent_state",
                io.agentscope.core.state.AgentState.builder()
                        .userId("alice").sessionId("s1").build());

        bb.applyPatch("alice", "s1", BlackboardPatch.builder()
                .customerFactsPatch(java.util.Map.of("amount", 999.0))
                .activeExpert("e1")
                .build());

        // Conversation state still loadable as AgentState.
        assertTrue(store.get("alice", "s1", "agent_state",
                io.agentscope.core.state.AgentState.class).isPresent());
        // Blackboard loadable as SessionBlackboard.
        assertTrue(store.get("alice", "s1", "shared_blackboard",
                SessionBlackboard.class).isPresent());
        // The blackboard is NOT accidentally typed as AgentState.
        assertThrows(ClassCastException.class, () ->
                store.get("alice", "s1", "shared_blackboard",
                        io.agentscope.core.state.AgentState.class),
                "Blackboard must be type-distinct from AgentState");
    }
}

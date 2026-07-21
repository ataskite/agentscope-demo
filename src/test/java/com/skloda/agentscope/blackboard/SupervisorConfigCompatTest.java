package com.skloda.agentscope.blackboard;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies backward compatibility per objective §Verification #7:
 * Legacy ROUTING / HANDOFFS agents that do NOT enable sharedBlackboard must keep
 * their original behavior — the {@link SupervisorRuntimeFactory#isSupervisor(AgentConfig)}
 * gate must return false for them so the AgentService never diverts to the new path.
 */
class SupervisorConfigCompatTest {

    @Test
    void legacyRoutingAgentWithoutSharedBlackboardIsNotSupervisor() {
        AgentConfig cfg = new AgentConfig();
        cfg.setAgentId("smart-router");
        cfg.setType(AgentType.ROUTING);
        // No sharedBlackboard set.

        assertFalse(SupervisorRuntimeFactory.isSupervisor(cfg),
                "Legacy ROUTING agent without sharedBlackboard must NOT trigger Supervisor path");
    }

    @Test
    void routingAgentWithSharedBlackboardDisabledIsNotSupervisor() {
        AgentConfig cfg = new AgentConfig();
        cfg.setAgentId("some-router");
        cfg.setType(AgentType.ROUTING);
        AgentConfig.SharedBlackboardConfig bb = new AgentConfig.SharedBlackboardConfig();
        bb.setEnabled(false); // explicitly disabled
        cfg.setSharedBlackboard(bb);

        assertFalse(SupervisorRuntimeFactory.isSupervisor(cfg),
                "sharedBlackboard.enabled=false must NOT trigger Supervisor path");
    }

    @Test
    void routingAgentWithSharedBlackboardEnabledIsSupervisor() {
        AgentConfig cfg = new AgentConfig();
        cfg.setAgentId("customer-service-supervisor");
        cfg.setType(AgentType.ROUTING);
        AgentConfig.SharedBlackboardConfig bb = new AgentConfig.SharedBlackboardConfig();
        bb.setEnabled(true);
        cfg.setSharedBlackboard(bb);

        assertTrue(SupervisorRuntimeFactory.isSupervisor(cfg));
    }

    @Test
    void defaultSharedBlackboardConfigIsDisabled() {
        AgentConfig.SharedBlackboardConfig bb = new AgentConfig.SharedBlackboardConfig();
        assertFalse(bb.isEnabled(), "Default must be disabled to preserve legacy behavior");
        assertEquals("shared_blackboard", bb.getStorageKey());
        assertEquals(0.5, bb.getMinConfidence(), 0.0001);
    }

    @Test
    void defaultRoutingConfigIsRuleWithDefaultKeep() {
        AgentConfig.RoutingConfig rc = new AgentConfig.RoutingConfig();
        assertEquals("rule", rc.getStrategy());
        assertTrue(rc.isDefaultKeep());
        assertEquals(3, rc.getRecentTurns());
    }

    @Test
    void handoffsAgentNeverTriggersSupervisorPathEvenIfBlackboardSet() {
        // Objective constraint #9: HANDOFFS stays on its original code path in this version.
        // AgentService gates Supervisor dispatch on (type == ROUTING && isSupervisor(cfg)),
        // so a HANDOFFS agent that happens to set sharedBlackboard.enabled=true must NOT
        // be diverted. This test documents that contract.
        AgentConfig cfg = new AgentConfig();
        cfg.setAgentId("customer-service");
        cfg.setType(AgentType.HANDOFFS);
        AgentConfig.SharedBlackboardConfig bb = new AgentConfig.SharedBlackboardConfig();
        bb.setEnabled(true);
        cfg.setSharedBlackboard(bb);

        assertEquals(AgentType.HANDOFFS, cfg.getType());
        // isSupervisor is flag-only — the type gate lives in AgentService:
        assertTrue(SupervisorRuntimeFactory.isSupervisor(cfg));
        // The effective dispatch condition is: type == ROUTING && isSupervisor(cfg)
        boolean wouldDispatchToSupervisor =
                cfg.getType() == AgentType.ROUTING && SupervisorRuntimeFactory.isSupervisor(cfg);
        assertFalse(wouldDispatchToSupervisor,
                "HANDOFFS agents must NOT dispatch to Supervisor even if flag is set");
    }
}

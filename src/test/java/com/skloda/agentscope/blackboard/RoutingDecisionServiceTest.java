package com.skloda.agentscope.blackboard;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.HandoffTrigger;
import com.skloda.agentscope.agent.SubAgentConfig;
import com.skloda.agentscope.agent.TriggerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the rule-based routing strategy produces deterministic KEEP / SWITCH / CLARIFY
 * outcomes per objective §Required behavior #1–#3.
 */
class RoutingDecisionServiceTest {

    private final RoutingDecisionService service = new RoutingDecisionService();

    private AgentConfig supervisorConfig(AgentConfig.RoutingConfig routingCfg) {
        AgentConfig cfg = new AgentConfig();
        cfg.setAgentId("sup");
        cfg.setType(com.skloda.agentscope.agent.AgentType.ROUTING);
        cfg.setSubAgents(List.of(
                SubAgentConfig.builder().agentId("support-agent").description("general").build(),
                SubAgentConfig.builder().agentId("sales-agent").description("sales").build(),
                SubAgentConfig.builder().agentId("complaint-agent").description("complaint").build()));
        cfg.setHandoffTriggers(List.of(
                HandoffTrigger.builder().type(TriggerType.INTENT)
                        .keywords(List.of("价格", "购买", "报价")).target("sales-agent").build(),
                HandoffTrigger.builder().type(TriggerType.INTENT)
                        .keywords(List.of("投诉", "不满")).target("complaint-agent").build(),
                HandoffTrigger.builder().type(TriggerType.EXPLICIT)
                        .keywords(List.of("转支持")).target("support-agent").build()));
        cfg.setRoutingConfig(routingCfg);
        return cfg;
    }

    @Test
    void firstTurnWithIntentMatchSwitchesToTargetExpert() {
        AgentConfig cfg = supervisorConfig(null);
        SessionBlackboard bb = new SessionBlackboard(); // version 0, no activeExpert

        RoutingDecisionService.RoutingDecision d =
                service.decide(cfg, "我想问问产品价格", bb);

        assertEquals(RoutingAction.SWITCH, d.getAction());
        assertEquals("sales-agent", d.getSelectedExpert());
        assertEquals(0, d.getBlackboardVersion());
        assertTrue(d.getReason().contains("INTENT"));
    }

    @Test
    void consecutiveFollowUpWithNoNewMatchDefaultsToKeep() {
        AgentConfig cfg = supervisorConfig(null);
        SessionBlackboard bb = new SessionBlackboard();
        bb.applyPatch(BlackboardPatch.builder().activeExpert("sales-agent").build());
        // activeExpert is now sales-agent, version 1

        // User asks a vague follow-up that matches no trigger.
        RoutingDecisionService.RoutingDecision d =
                service.decide(cfg, "嗯，继续说说看", bb);

        assertEquals(RoutingAction.KEEP, d.getAction());
        assertEquals("sales-agent", d.getSelectedExpert(),
                "defaultKeep=true must retain current expert on vague follow-up");
        assertEquals(1, d.getBlackboardVersion());
    }

    @Test
    void defaultKeepFalseProducesClarifyOnNoMatch() {
        AgentConfig.RoutingConfig rc = new AgentConfig.RoutingConfig();
        rc.setDefaultKeep(false);
        AgentConfig cfg = supervisorConfig(rc);
        SessionBlackboard bb = new SessionBlackboard();
        bb.applyPatch(BlackboardPatch.builder().activeExpert("sales-agent").build());

        RoutingDecisionService.RoutingDecision d =
                service.decide(cfg, "嗯，继续说说看", bb);

        assertEquals(RoutingAction.CLARIFY, d.getAction());
        assertNull(d.getSelectedExpert(), "CLARIFY must not route to any expert");
        assertTrue(d.getConfidence() < 0.5, "CLARIFY confidence must be low");
    }

    @Test
    void explicitTriggerBeatsIntent() {
        AgentConfig cfg = supervisorConfig(null);
        SessionBlackboard bb = new SessionBlackboard();
        bb.applyPatch(BlackboardPatch.builder().activeExpert("sales-agent").build());

        // The text "转支持" is an EXPLICIT trigger; even though no intent keyword matches,
        // EXPLICIT takes precedence and routes to support-agent.
        RoutingDecisionService.RoutingDecision d =
                service.decide(cfg, "请帮我转支持", bb);

        assertEquals(RoutingAction.SWITCH, d.getAction());
        assertEquals("support-agent", d.getSelectedExpert());
        assertTrue(d.getReason().contains("EXPLICIT"));
    }

    @Test
    void intentMatchOnCurrentActiveExpertIsKeep() {
        AgentConfig cfg = supervisorConfig(null);
        SessionBlackboard bb = new SessionBlackboard();
        bb.applyPatch(BlackboardPatch.builder().activeExpert("sales-agent").build());

        // User mentions 价格 again — same expert, so KEEP (not SWITCH).
        RoutingDecisionService.RoutingDecision d =
                service.decide(cfg, "价格能不能再优惠点", bb);

        assertEquals(RoutingAction.KEEP, d.getAction());
        assertEquals("sales-agent", d.getSelectedExpert());
    }

    @Test
    void intentMatchOnDifferentExpertIsSwitch() {
        AgentConfig cfg = supervisorConfig(null);
        SessionBlackboard bb = new SessionBlackboard();
        bb.applyPatch(BlackboardPatch.builder().activeExpert("sales-agent").build());

        RoutingDecisionService.RoutingDecision d =
                service.decide(cfg, "我对这次购买很不满，要投诉", bb);

        assertEquals(RoutingAction.SWITCH, d.getAction());
        assertEquals("complaint-agent", d.getSelectedExpert(),
                "INTENT match on different expert must SWITCH, preserving blackboard facts");
    }

    @Test
    void firstTurnWithNoMatchAndNoExpertsYieldsClarify() {
        AgentConfig cfg = new AgentConfig();
        cfg.setAgentId("empty-sup");
        cfg.setRoutingConfig(null);
        // no subAgents, no triggers
        SessionBlackboard bb = new SessionBlackboard();

        RoutingDecisionService.RoutingDecision d = service.decide(cfg, "hello", bb);
        assertEquals(RoutingAction.CLARIFY, d.getAction());
    }

    @Test
    void firstTurnWithNoMatchButHasExpertsRoutesToFirst() {
        // defaultKeep=true (default RoutingConfig) → first declared expert, not CLARIFY,
        // so the user isn't blocked on turn 1.
        AgentConfig cfg = supervisorConfig(null);
        SessionBlackboard bb = new SessionBlackboard();

        RoutingDecisionService.RoutingDecision d = service.decide(cfg, "你好", bb);
        assertEquals(RoutingAction.SWITCH, d.getAction());
        assertEquals("support-agent", d.getSelectedExpert());
    }
}

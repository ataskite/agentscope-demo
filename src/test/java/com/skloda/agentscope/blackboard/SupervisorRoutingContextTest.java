package com.skloda.agentscope.blackboard;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.HandoffTrigger;
import com.skloda.agentscope.agent.SubAgentConfig;
import com.skloda.agentscope.agent.TriggerType;
import io.agentscope.core.state.InMemoryAgentStateStore;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end (LLM-free) simulation of the objective's reference scenario
 * (§Verification #3):
 *
 * <blockquote>
 * 第 1 轮由还款专家处理，写入 settlementAmount 和 failureReason。
 * 第 2 轮用户转为投诉意图。
 * 投诉专家能从 Blackboard 读取上一专家产生的业务事实。
 * 当前 activeExpert 更新为投诉专家。
 * 用户无需重复提供金额和失败原因。
 * </blockquote>
 *
 * <p>We exercise the real {@link BlackboardService} and {@link RoutingDecisionService} and
 * simulate expert replies by applying patches through the same public API the
 * SupervisorRuntime uses. This proves that (a) the data contracts carry facts across
 * expert switches, (b) the router picks the right expert on each turn, and (c) the
 * blackboard version grows monotonically.
 */
class SupervisorRoutingContextTest {

    @Test
    void factsWrittenByExpertAAreVisibleToExpertBAfterSwitch() {
        BlackboardService blackboard = new BlackboardService(
                new InMemoryAgentStateStore(), "shared_blackboard");
        RoutingDecisionService router = new RoutingDecisionService();
        AgentConfig sup = buildSupervisorConfig();

        String userId = "alice";
        String sessionId = "s1";
        blackboard.getOrCreate(userId, sessionId);

        // ---- Turn 1: user reports a failed repayment ----
        String turn1 = "我的订单还款失败了，金额 1200 元，提示超时";
        SessionBlackboard bb1 = blackboard.getOrCreate(userId, sessionId);
        RoutingDecisionService.RoutingDecision d1 = router.decide(sup, turn1, bb1);
        assertEquals(RoutingAction.SWITCH, d1.getAction());
        assertEquals("repayment-expert", d1.getSelectedExpert(),
                "Turn 1 must route to repayment-expert");

        // Simulate repayment-expert's reply: writes settlementAmount + failureReason.
        // The Supervisor is the sole writer, so it calls applyPatch with the expert's patch.
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("settlementAmount", 1200.0);
        facts.put("failureReason", "timeout");
        BlackboardPatch patchFromExpertA = BlackboardPatch.builder()
                .customerFactsPatch(facts)
                .findingsToAdd(List.of(
                        new ExpertFinding("repayment-expert", "settlementAmount",
                                1200.0, "user-reported"),
                        new ExpertFinding("repayment-expert", "failureReason",
                                "timeout", "system error code")))
                .build();
        BlackboardPatch effectivePatch = coerce(patchFromExpertA, d1, turn1);
        SessionBlackboard bb1Post = blackboard.applyPatch(userId, sessionId, effectivePatch);

        assertEquals(1, bb1Post.getVersion());
        assertEquals("repayment-expert", bb1Post.getActiveExpert());
        assertEquals(1200.0, bb1Post.getCustomerFacts().get("settlementAmount"));
        assertEquals("timeout", bb1Post.getCustomerFacts().get("failureReason"));

        // ---- Turn 2: user escalates to a complaint ----
        String turn2 = "我对这次失败非常不满，要投诉，要求赔偿";
        SessionBlackboard bb2 = blackboard.getOrCreate(userId, sessionId);
        RoutingDecisionService.RoutingDecision d2 = router.decide(sup, turn2, bb2);

        assertEquals(RoutingAction.SWITCH, d2.getAction());
        assertEquals("complaint-expert", d2.getSelectedExpert(),
                "Turn 2 must SWITCH to complaint-expert");
        assertNotEquals(d1.getSelectedExpert(), d2.getSelectedExpert(),
                "Turn 2 expert must differ from turn 1");

        // The complaint expert receives a blackboard snapshot via ExpertRequest.
        SessionBlackboard snapshotForExpertB = bb2.snapshot();
        ExpertRequest reqForB = new ExpertRequest(
                "complaint-expert", turn2, /*summary*/ "",
                List.of(), snapshotForExpertB, d2.getReason(), "repayment-expert");

        // Assert: facts written by expert A are visible to expert B via the snapshot.
        assertEquals(1200.0, reqForB.getBlackboardSnapshot().getCustomerFacts().get("settlementAmount"),
                "Expert B must see the settlementAmount written by expert A");
        assertEquals("timeout", reqForB.getBlackboardSnapshot().getCustomerFacts().get("failureReason"),
                "Expert B must see the failureReason written by expert A");
        assertEquals(2, reqForB.getBlackboardSnapshot().getFindings().size(),
                "Findings audit trail from expert A must be carried over");

        // Supervisor applies expert B's patch (B adds a complaint-specific fact).
        BlackboardPatch patchFromExpertB = BlackboardPatch.builder()
                .businessStatePatch(Map.of("complaintStatus", "open"))
                .unresolvedQuestions(List.of("请问您的订单号是多少？"))
                .build();
        BlackboardPatch effectivePatchB = coerce(patchFromExpertB, d2, turn2);
        SessionBlackboard bb2Post = blackboard.applyPatch(userId, sessionId, effectivePatchB);

        assertEquals(2, bb2Post.getVersion(), "Version must be 2 after two patches");
        assertEquals("complaint-expert", bb2Post.getActiveExpert(),
                "activeExpert must now point to the complaint expert");
        assertEquals("open", bb2Post.getBusinessState().get("complaintStatus"));
        // Original facts from expert A must STILL be present — no overwrite.
        assertEquals(1200.0, bb2Post.getCustomerFacts().get("settlementAmount"),
                "Expert A's facts must survive the switch to expert B");
        assertEquals("timeout", bb2Post.getCustomerFacts().get("failureReason"));

        assertEquals(1, bb2Post.getUnresolvedQuestions().size());
        assertEquals("请问您的订单号是多少？", bb2Post.getUnresolvedQuestions().get(0));
    }

    @Test
    void consecutiveFollowUpTurnsKeepCurrentExpert() {
        BlackboardService blackboard = new BlackboardService(
                new InMemoryAgentStateStore(), "shared_blackboard");
        RoutingDecisionService router = new RoutingDecisionService();
        AgentConfig sup = buildSupervisorConfig();

        blackboard.getOrCreate("u", "s");
        blackboard.applyPatch("u", "s", BlackboardPatch.builder()
                .activeExpert("repayment-expert").build());

        // Vague follow-up matches no trigger.
        RoutingDecisionService.RoutingDecision d =
                router.decide(sup, "嗯，然后呢？", blackboard.getOrCreate("u", "s"));
        assertEquals(RoutingAction.KEEP, d.getAction());
        assertEquals("repayment-expert", d.getSelectedExpert());
    }

    @Test
    void clarifyWhenConfidenceLowAndDefaultKeepFalse() {
        BlackboardService blackboard = new BlackboardService(
                new InMemoryAgentStateStore(), "shared_blackboard");
        RoutingDecisionService router = new RoutingDecisionService();

        AgentConfig sup = buildSupervisorConfig();
        AgentConfig.RoutingConfig rc = new AgentConfig.RoutingConfig();
        rc.setDefaultKeep(false);
        sup.setRoutingConfig(rc);

        blackboard.getOrCreate("u", "s");
        blackboard.applyPatch("u", "s", BlackboardPatch.builder()
                .activeExpert("repayment-expert").build());

        RoutingDecisionService.RoutingDecision d =
                router.decide(sup, "嗯，然后呢？", blackboard.getOrCreate("u", "s"));
        assertEquals(RoutingAction.CLARIFY, d.getAction());
        assertNull(d.getSelectedExpert());
    }

    /**
     * Reproduce the SupervisorRuntime.coercePatchMetadata step so the test exercises
     * the exact same merge semantics the production runtime uses.
     */
    private static BlackboardPatch coerce(BlackboardPatch patch,
                                          RoutingDecisionService.RoutingDecision decision,
                                          String userText) {
        String active = patch.getActiveExpert() != null
                ? patch.getActiveExpert() : decision.getSelectedExpert();
        String intent = patch.getCurrentIntent() != null
                ? patch.getCurrentIntent()
                : (userText.length() > 60 ? userText.substring(0, 60) + "…" : userText);
        return BlackboardPatch.builder()
                .activeExpert(active)
                .currentIntent(intent)
                .customerFactsPatch(patch.getCustomerFactsPatch())
                .collectedSlotsPatch(patch.getCollectedSlotsPatch())
                .businessStatePatch(patch.getBusinessStatePatch())
                .findingsToAdd(patch.getFindingsToAdd())
                .unresolvedQuestions(patch.getUnresolvedQuestions())
                .build();
    }

    private AgentConfig buildSupervisorConfig() {
        AgentConfig cfg = new AgentConfig();
        cfg.setAgentId("sup");
        cfg.setType(com.skloda.agentscope.agent.AgentType.ROUTING);
        cfg.setSubAgents(List.of(
                SubAgentConfig.builder().agentId("repayment-expert").description("还款").build(),
                SubAgentConfig.builder().agentId("complaint-expert").description("投诉").build()));
        cfg.setHandoffTriggers(List.of(
                HandoffTrigger.builder().type(TriggerType.INTENT)
                        .keywords(List.of("还款", "订单", "失败")).target("repayment-expert").build(),
                HandoffTrigger.builder().type(TriggerType.INTENT)
                        .keywords(List.of("投诉", "不满", "赔偿")).target("complaint-expert").build()));
        return cfg;
    }
}

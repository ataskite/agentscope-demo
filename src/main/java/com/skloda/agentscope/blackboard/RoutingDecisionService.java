package com.skloda.agentscope.blackboard;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.HandoffTrigger;
import com.skloda.agentscope.agent.TriggerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Decides which expert handles the next turn, using the static strategy configured in
 * {@link com.skloda.agentscope.agent.AgentConfig.RoutingConfig}.
 *
 * <p>Strategies:
 * <ul>
 *   <li><b>"rule"</b> (default): deterministic keyword match on the agent's
 *       {@link HandoffTrigger} list. {@code INTENT} triggers select their {@code target}
 *       expert; {@code EXPLICIT} triggers take precedence (user said "转 XX"). When no
 *       trigger matches and the blackboard has an {@code activeExpert},
 *       {@code defaultKeep=true} yields KEEP; otherwise CLARIFY. The first turn (no
 *       activeExpert, no match) routes to the first sub-agent as a sensible default
 *       rather than blocking the user on CLARIFY — tunable via {@code defaultKeep}.</li>
 *   <li><b>"llm"</b>: Supervisor LLM produces structured JSON (pluggable; in this version
 *       we expose the strategy hook but route through the same rule fallback to avoid
 *       requiring a model call in tests). See {@code SupervisorRuntime} for the LLM path.</li>
 * </ul>
 *
 * <p>This service is pure / stateless — all inputs are explicit arguments. The runtime
 * state (activeExpert, currentIntent) comes from the blackboard snapshot, never from YAML.
 */
@Service
public class RoutingDecisionService {

    private static final Logger log = LoggerFactory.getLogger(RoutingDecisionService.class);

    /**
     * Single-turn routing decision.
     *
     * @param supervisorConfig the router agent's static config (experts + triggers + routingConfig)
     * @param userMessage      the current user turn
     * @param blackboard       current blackboard snapshot (may be fresh / version 0)
     * @return a routing decision with action + selectedExpert + reason
     */
    public RoutingDecision decide(AgentConfig supervisorConfig,
                                  String userMessage,
                                  SessionBlackboard blackboard) {
        Objects.requireNonNull(supervisorConfig, "supervisorConfig");
        Objects.requireNonNull(blackboard, "blackboard");
        String msg = userMessage == null ? "" : userMessage;

        AgentConfig.RoutingConfig cfg = supervisorConfig.getRoutingConfig();
        String strategy = cfg != null && cfg.getStrategy() != null && !cfg.getStrategy().isBlank()
                ? cfg.getStrategy().toLowerCase() : "rule";

        return switch (strategy) {
            case "llm" -> decideLlm(supervisorConfig, cfg, msg, blackboard);
            default -> decideRule(supervisorConfig, cfg, msg, blackboard);
        };
    }

    // ---- Rule strategy ----

    private RoutingDecision decideRule(AgentConfig cfg,
                                       AgentConfig.RoutingConfig routingCfg,
                                       String msg,
                                       SessionBlackboard bb) {
        List<HandoffTrigger> triggers = cfg.getHandoffTriggers();
        String activeExpert = bb.getActiveExpert();
        boolean defaultKeep = routingCfg == null || routingCfg.isDefaultKeep();

        // Note: HandoffTrigger.matches() only fires on INTENT triggers — it returns false
        // for EXPLICIT. We do keyword matching ourselves here so both trigger types work.
        // 1. EXPLICIT triggers (user explicitly asked to switch) take absolute precedence.
        if (triggers != null) {
            for (HandoffTrigger t : triggers) {
                if (t.getType() == TriggerType.EXPLICIT && keywordMatch(t.getKeywords(), msg)) {
                    return RoutingDecision.switchTo(t.getTarget(),
                            "EXPLICIT trigger matched: " + t.getKeywords(),
                            bb.getVersion());
                }
            }
            // 2. INTENT triggers — collect all matches, then prefer a SWITCH over KEEP.
            //    Rationale: if the user's text hits multiple intents and at least one points
            //    to a different expert than the current one, the user most likely wants to
            //    switch context (e.g. "我对这次购买很不满，要投诉" matches both 销售 and 投诉
            //    intents while activeExpert=sales-agent → SWITCH to complaint-agent).
            java.util.List<HandoffTrigger> intentHits = new java.util.ArrayList<>();
            for (HandoffTrigger t : triggers) {
                if (t.getType() == TriggerType.INTENT
                        && keywordMatch(t.getKeywords(), msg)
                        && t.getTarget() != null) {
                    intentHits.add(t);
                }
            }
            if (!intentHits.isEmpty()) {
                for (HandoffTrigger t : intentHits) {
                    if (!t.getTarget().equals(activeExpert)) {
                        return RoutingDecision.switchTo(t.getTarget(),
                                "INTENT trigger matched and differs from activeExpert: "
                                        + t.getKeywords(),
                                bb.getVersion());
                    }
                }
                // All hits point to the current expert.
                return RoutingDecision.keep(activeExpert,
                        "INTENT matched current activeExpert; KEEP",
                        bb.getVersion());
            }
        }

        // 3. No trigger matched.
        if (activeExpert != null) {
            if (defaultKeep) {
                return RoutingDecision.keep(activeExpert,
                        "No trigger matched; defaulting to KEEP current expert",
                        bb.getVersion());
            }
            return RoutingDecision.clarify("No trigger matched and defaultKeep=false",
                    bb.getVersion());
        }

        // 4. First turn ever (no activeExpert, no match). Route to the first declared
        //    expert so the user isn't blocked. Tunable: if defaultKeep=false the caller
        //    can interpret this as CLARIFY by configuration.
        List<com.skloda.agentscope.agent.SubAgentConfig> subs = cfg.getSubAgents();
        if (subs != null && !subs.isEmpty()) {
            String firstExpert = subs.get(0).getAgentId();
            return RoutingDecision.switchTo(firstExpert,
                    "First turn with no activeExpert; routing to first declared expert",
                    bb.getVersion());
        }
        return RoutingDecision.clarify("No experts configured", bb.getVersion());
    }

    private static boolean keywordMatch(List<String> keywords, String text) {
        if (keywords == null || keywords.isEmpty() || text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        return keywords.stream().anyMatch(kw -> kw != null && lower.contains(kw.toLowerCase()));
    }

    // ---- LLM strategy (hook; falls back to rule in this version) ----

    private RoutingDecision decideLlm(AgentConfig cfg,
                                      AgentConfig.RoutingConfig routingCfg,
                                      String msg,
                                      SessionBlackboard bb) {
        // The LLM call is performed by SupervisorRuntime (which owns the Model instance).
        // When the runtime delegates to this service for the "llm" strategy, it pre-empts
        // by calling decide() only after its own LLM routing fails or returns low confidence.
        // To keep this service pure and side-effect-free, we fall back to the rule strategy
        // here and let the runtime layer override when it has a confident LLM answer.
        log.debug("LLM routing strategy requested but no inline model; falling back to rule");
        return decideRule(cfg, routingCfg, msg, bb);
    }

    // ---- Decision value object ----

    public static final class RoutingDecision {
        private final RoutingAction action;
        private final String selectedExpert;
        private final String reason;
        private final long blackboardVersion;
        private final double confidence;

        private RoutingDecision(RoutingAction action, String selectedExpert,
                                String reason, long version, double confidence) {
            this.action = action;
            this.selectedExpert = selectedExpert;
            this.reason = reason;
            this.blackboardVersion = version;
            this.confidence = confidence;
        }

        public static RoutingDecision keep(String expert, String reason, long version) {
            return new RoutingDecision(RoutingAction.KEEP, expert, reason, version, 0.9);
        }

        public static RoutingDecision switchTo(String expert, String reason, long version) {
            return new RoutingDecision(RoutingAction.SWITCH, expert, reason, version, 0.85);
        }

        public static RoutingDecision clarify(String reason, long version) {
            return new RoutingDecision(RoutingAction.CLARIFY, null, reason, version, 0.2);
        }

        /** Builder-style override for the LLM path (supplies confidence). */
        public RoutingDecision withConfidence(double c) {
            return new RoutingDecision(action, selectedExpert, reason, blackboardVersion, c);
        }

        public RoutingAction getAction() { return action; }
        public String getSelectedExpert() { return selectedExpert; }
        public String getReason() { return reason; }
        public long getBlackboardVersion() { return blackboardVersion; }
        public double getConfidence() { return confidence; }

        @Override
        public String toString() {
            return "RoutingDecision{" + action
                    + (selectedExpert != null ? " -> " + selectedExpert : "")
                    + ", v=" + blackboardVersion
                    + ", conf=" + confidence
                    + ", reason=" + reason + '}';
        }
    }
}

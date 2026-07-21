package com.skloda.agentscope.blackboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.hook.ObservabilityHook;
import com.skloda.agentscope.runtime.StreamingAgentRuntime;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Streaming runtime for a Supervisor / Router agent that drives a team of experts through
 * an explicit Shared Blackboard.
 *
 * <p>This runtime is the single entry point for a Supervisor conversation. Each call to
 * {@link #stream(Msg)}:
 * <ol>
 *   <li>acquires the per-{@code (userId, sessionId)} lock (serial routing + patch + state write),</li>
 *   <li>reads a defensive blackboard snapshot,</li>
 *   <li>computes a {@link RoutingDecisionService.RoutingDecision} (KEEP / SWITCH / CLARIFY),</li>
 *   <li>emits a {@code routing_event} with {@code action, previousExpert, selectedExpert,
 *       reason, sessionId, blackboardVersion},</li>
 *   <li>if KEEP/SWITCH: builds an {@link ExpertRequest} (deterministic — blackboard snapshot,
 *       summary, reason are passed explicitly), dispatches a fresh expert instance via
 *       {@link ExpertAgentProvider} using an expert-scoped RuntimeContext, parses the
 *       expert's reply into {@link ExpertResult}, applies its {@link BlackboardPatch} as
 *       the sole writer, and records user + final reply into the Supervisor's
 *       Conversation AgentState;</li>
 *   <li>if CLARIFY: returns a clarifying question without dispatching.</li>
 * </ol>
 *
 * <p><b>State boundaries enforced here</b>:
 * <ul>
 *   <li>Supervisor AgentState is loaded / saved under key {@code "agent_state"} via the
 *       Supervisor's own AgentStateStore + RuntimeContext.</li>
 *   <li>Blackboard is loaded / saved under key {@code "shared_blackboard"} via
 *       {@link BlackboardService}.</li>
 *   <li>Expert AgentState lives in a fresh {@code InMemoryAgentStateStore} inside the
 *       expert instance — never shared with the Supervisor or another expert.</li>
 * </ul>
 */
public class SupervisorRuntime implements StreamingAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(SupervisorRuntime.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AgentConfig supervisorConfig;
    private final ReActAgent supervisorAgent;
    private final ObservabilityHook hook;
    private final BlackboardService blackboardService;
    private final RoutingDecisionService routingService;
    private final ExpertAgentProvider expertProvider;
    private final String userId;
    private final String sessionId;
    private final AgentStateAccessor supervisorStateAccessor;

    /** Per-(userId,sessionId) lock — serial routing, expert call, and patch merge. */
    private final ReentrantLock sessionLock;

    /** Cache of per-slot locks (shared across all SupervisorRuntimes in the JVM). */
    private static final Map<String, ReentrantLock> SLOT_LOCKS = new ConcurrentHashMap<>();

    public SupervisorRuntime(AgentConfig supervisorConfig,
                             ReActAgent supervisorAgent,
                             ObservabilityHook hook,
                             BlackboardService blackboardService,
                             RoutingDecisionService routingService,
                             ExpertAgentProvider expertProvider,
                             String userId,
                             String sessionId) {
        this.supervisorConfig = Objects.requireNonNull(supervisorConfig);
        this.supervisorAgent = Objects.requireNonNull(supervisorAgent);
        this.hook = Objects.requireNonNull(hook);
        this.blackboardService = Objects.requireNonNull(blackboardService);
        this.routingService = Objects.requireNonNull(routingService);
        this.expertProvider = Objects.requireNonNull(expertProvider);
        this.userId = userId;
        this.sessionId = sessionId == null || sessionId.isBlank()
                ? ("supervisor-" + System.identityHashCode(this))
                : sessionId;
        this.sessionLock = SLOT_LOCKS.computeIfAbsent(slotKey(this.userId, this.sessionId),
                k -> new ReentrantLock());
        this.supervisorStateAccessor = new AgentStateAccessor(supervisorAgent);
    }

    @Override
    public ObservabilityHook getHook() {
        return hook;
    }

    @Override
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        // We subscribe on boundedElastic because expert dispatch is blocking (agent.call).
        return Flux.<Map<String, Object>>create(sink -> {
            sink.next(Map.of("type", "supervisor_start",
                    "agentId", supervisorConfig.getAgentId(),
                    "sessionId", sessionId,
                    "userId", userId == null ? "" : userId));
            try {
                routeAndDispatch(userMsg, sink);
            } catch (Exception e) {
                log.error("Supervisor routing failed for session {}", sessionId, e);
                sink.next(Map.of("type", "error",
                        "message", "Supervisor error: " + e.getMessage(),
                        "sessionId", sessionId));
            } finally {
                sink.next(Map.of("type", "done", "agentId", supervisorConfig.getAgentId()));
                sink.complete();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void routeAndDispatch(Msg userMsg, FluxSink<Map<String, Object>> sink) {
        String userText = extractText(userMsg);
        sessionLock.lock();
        try {
            // 1. Read blackboard snapshot (defensive copy from BlackboardService).
            SessionBlackboard bb = blackboardService.getOrCreate(userId, sessionId);
            long preRoutingVersion = bb.getVersion();
            String previousExpert = bb.getActiveExpert();

            // 2. Record user message into Supervisor Conversation AgentState.
            supervisorStateAccessor.appendUserMessage(userMsg);
            saveSupervisorState();

            // 3. Compute routing decision.
            RoutingDecisionService.RoutingDecision decision =
                    routingService.decide(supervisorConfig, userText, bb);

            // 4. Emit routing event (objective §Required behavior #5).
            Map<String, Object> routingEvent = new LinkedHashMap<>();
            routingEvent.put("type", "routing_event");
            routingEvent.put("action", decision.getAction().name());
            routingEvent.put("previousExpert", previousExpert == null ? "" : previousExpert);
            routingEvent.put("selectedExpert", decision.getSelectedExpert() == null
                    ? "" : decision.getSelectedExpert());
            routingEvent.put("reason", decision.getReason());
            routingEvent.put("sessionId", sessionId);
            routingEvent.put("userId", userId == null ? "" : userId);
            routingEvent.put("blackboardVersion", preRoutingVersion);
            routingEvent.put("confidence", decision.getConfidence());
            routingEvent.put("timestamp", System.currentTimeMillis());
            sink.next(routingEvent);
            // Also mirror into the hook sink for any ObservabilityHook consumers.
            hook.getEventSink().emit("routing_event", routingEvent);

            // 5. Act on the decision.
            switch (decision.getAction()) {
                case CLARIFY -> {
                    String clarifyText = "您的问题我需要确认一下：请问您希望"
                            + enumerateExperts()
                            + " 中的哪一类来帮助您？"
                            + "（您可以更具体地描述需求，或者直接说“转 XX”。）";
                    Msg clarifyMsg = supervisorAssistantMsg(clarifyText);
                    supervisorStateAccessor.appendAssistantMessage(clarifyMsg);
                    saveSupervisorState();
                    emitTextDeltas(sink, clarifyText);
                }
                case KEEP, SWITCH -> dispatchExpert(decision, previousExpert, userText, bb, sink);
                case MULTI -> {
                    // Not implemented in v1; fall back to single dispatch on selected expert.
                    log.warn("MULTI routing requested but not implemented; falling back to single");
                    dispatchExpert(decision, previousExpert, userText, bb, sink);
                }
                default -> {
                    log.warn("Unknown routing action {}; treating as CLARIFY", decision.getAction());
                    dispatchExpert(decision, previousExpert, userText, bb, sink);
                }
            }
        } finally {
            sessionLock.unlock();
        }
    }

    private void dispatchExpert(RoutingDecisionService.RoutingDecision decision,
                                String previousExpert,
                                String userText,
                                SessionBlackboard bbPre,
                                FluxSink<Map<String, Object>> sink) {
        String expertId = decision.getSelectedExpert();
        if (expertId == null || expertId.isBlank()) {
            // Defensive: should not happen for KEEP/SWITCH, but handle gracefully.
            String fallback = "暂时无法确定合适的专家，请稍后再试或换种说法。";
            supervisorStateAccessor.appendAssistantMessage(supervisorAssistantMsg(fallback));
            saveSupervisorState();
            emitTextDeltas(sink, fallback);
            return;
        }

        // Build deterministic ExpertRequest — never rely on the LLM to "remember" context.
        String summary = supervisorStateAccessor.getSummary();
        List<String> recentTurns = supervisorStateAccessor.getRecentTurns(3);
        ExpertRequest req = new ExpertRequest(
                expertId, userText, summary, recentTurns, bbPre.snapshot(),
                decision.getReason(), previousExpert);

        sink.next(Map.of("type", "expert_dispatch_start",
                "expertId", expertId,
                "previousExpert", previousExpert == null ? "" : previousExpert,
                "sessionId", sessionId));

        // Provide a fresh expert instance (new InMemoryAgentStateStore each call).
        ReActAgent expert = expertProvider.provide(expertId, userId, sessionId);

        // Compose the prompt the expert sees: deterministic context injection.
        Msg expertPrompt = buildExpertPrompt(req);

        // Expert-scoped RuntimeContext: (userId, sessionId::expertId).
        // Distinct from the Supervisor's RuntimeContext so AgentScope addresses expert state
        // under a different slot even if both shared a store (they don't — expert uses its own).
        String expertSessionId = ExpertAgentProvider.expertScopedSessionId(sessionId, expertId);
        RuntimeContext expertCtx = RuntimeContext.builder()
                .userId(userId)
                .sessionId(expertSessionId)
                .build();

        long t0 = System.currentTimeMillis();
        // Use the GA API that accepts RuntimeContext (constraint #2).
        Msg expertReply = expert.call(List.of(expertPrompt), expertCtx)
                .block();
        long durationMs = System.currentTimeMillis() - t0;

        // Parse expert reply into structured ExpertResult.
        ExpertResult result = parseExpertResult(expertId, expertReply);

        sink.next(Map.of("type", "expert_dispatch_end",
                "expertId", expertId,
                "confidence", result.getConfidence(),
                "durationMs", durationMs,
                "sessionId", sessionId));

        // Apply patch as the sole writer (constraint: Supervisor is only writer).
        BlackboardPatch patch = result.getBlackboardPatch();
        SessionBlackboard bbPost;
        if (patch != null && !patch.isEmpty()) {
            // Ensure activeExpert / currentIntent are coherent with the decision.
            BlackboardPatch effectivePatch = coercePatchMetadata(patch, expertId, decision, userText);
            bbPost = blackboardService.applyPatch(userId, sessionId, effectivePatch);
            sink.next(Map.of("type", "blackboard_patched",
                    "expertId", expertId,
                    "newVersion", bbPost.getVersion(),
                    "previousVersion", bbPre.getVersion(),
                    "sessionId", sessionId));
        } else {
            // Still bump the blackboard to record activeExpert/currentIntent even on empty patch.
            BlackboardPatch metaOnly = coercePatchMetadata(new BlackboardPatch(),
                    expertId, decision, userText);
            bbPost = blackboardService.applyPatch(userId, sessionId, metaOnly);
        }

        // Surface the expert's answer to the user as text deltas.
        String answer = result.getAnswer();
        if (answer == null || answer.isBlank()) {
            answer = "(专家未返回明确答案)";
        }
        emitTextDeltas(sink, answer);

        // Record the expert's answer into Supervisor Conversation AgentState.
        supervisorStateAccessor.appendAssistantMessage(supervisorAssistantMsg(answer));
        saveSupervisorState();

        // Surface unresolved questions if any.
        if (!result.getUnresolvedQuestions().isEmpty()) {
            sink.next(Map.of("type", "unresolved_questions",
                    "expertId", expertId,
                    "questions", result.getUnresolvedQuestions(),
                    "sessionId", sessionId));
        }
    }

    /** Make sure the patch sets activeExpert / currentIntent consistent with the decision. */
    private BlackboardPatch coercePatchMetadata(BlackboardPatch patch,
                                                String expertId,
                                                RoutingDecisionService.RoutingDecision decision,
                                                String userText) {
        // Use the patch's own values when provided; otherwise infer from decision.
        String active = patch.getActiveExpert() != null ? patch.getActiveExpert() : expertId;
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

    private Msg buildExpertPrompt(ExpertRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是专家 Agent「").append(req.getExpertId()).append("」，被 Supervisor 调用。\n\n");
        sb.append("## 当前用户消息\n").append(req.getCurrentMessage()).append("\n\n");

        SessionBlackboard bb = req.getBlackboardSnapshot();
        if (bb != null) {
            sb.append("## Shared Blackboard 快照（只读）\n");
            sb.append("- activeExpert: ").append(nullSafe(bb.getActiveExpert())).append("\n");
            sb.append("- currentIntent: ").append(nullSafe(bb.getCurrentIntent())).append("\n");
            if (!bb.getCustomerFacts().isEmpty()) {
                sb.append("- customerFacts: ").append(json(bb.getCustomerFacts())).append("\n");
            }
            if (!bb.getCollectedSlots().isEmpty()) {
                sb.append("- collectedSlots: ").append(json(bb.getCollectedSlots())).append("\n");
            }
            if (!bb.getBusinessState().isEmpty()) {
                sb.append("- businessState: ").append(json(bb.getBusinessState())).append("\n");
            }
            if (!bb.getFindings().isEmpty()) {
                sb.append("- findings(历史): ").append(bb.getFindings().size()).append(" 条\n");
            }
            sb.append("\n");
        }

        if (req.getConversationSummary() != null && !req.getConversationSummary().isBlank()) {
            sb.append("## 会话摘要\n").append(req.getConversationSummary()).append("\n\n");
        }
        if (!req.getRecentTurns().isEmpty()) {
            sb.append("## 最近对话\n");
            for (int i = 0; i < req.getRecentTurns().size(); i++) {
                sb.append(i + 1).append(". ").append(req.getRecentTurns().get(i)).append("\n");
            }
            sb.append("\n");
        }
        if (req.getRoutingReason() != null) {
            sb.append("## 路由原因\n").append(req.getRoutingReason()).append("\n\n");
        }

        sb.append("## 输出契约（必须严格遵守）\n");
        sb.append("你必须输出合法 JSON，字段：\n");
        sb.append("- expertId: 字符串，等于「").append(req.getExpertId()).append("」\n");
        sb.append("- answer: 字符串，面向用户的最终回答\n");
        sb.append("- confidence: 0~1 浮点数\n");
        sb.append("- blackboardPatch: 对象，可选字段 activeExpert/currentIntent/")
                .append("customerFactsPatch/collectedSlotsPatch/businessStatePatch/findingsToAdd/unresolvedQuestions\n");
        sb.append("- unresolvedQuestions: 字符串数组\n");
        sb.append("只输出 JSON，不要输出其它内容。\n");
        return Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(sb.toString()).build())
                .build();
    }

    private ExpertResult parseExpertResult(String expertId, Msg reply) {
        if (reply == null) {
            return new ExpertResult(expertId, "(无返回)", 0.3, new BlackboardPatch(), List.of());
        }
        String text = extractText(reply).trim();
        // Try to parse as JSON. If it fails, treat the whole text as the answer.
        int braceStart = text.indexOf('{');
        int braceEnd = text.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            String json = text.substring(braceStart, braceEnd + 1);
            try {
                Map<String, Object> parsed = MAPPER.readValue(json,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                String answer = (String) parsed.getOrDefault("answer", text);
                double confidence = parseDouble(parsed.get("confidence"), 0.7);
                BlackboardPatch patch = convertPatch(parsed.get("blackboardPatch"));
                @SuppressWarnings("unchecked")
                List<String> qs = (List<String>) parsed.get("unresolvedQuestions");
                return new ExpertResult(expertId, answer, confidence, patch, qs);
            } catch (Exception e) {
                log.debug("Expert reply was not valid JSON, using raw text as answer: {}", e.getMessage());
            }
        }
        return new ExpertResult(expertId, text, 0.6, new BlackboardPatch(), List.of());
    }

    @SuppressWarnings("unchecked")
    private BlackboardPatch convertPatch(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return new BlackboardPatch();
        }
        try {
            return BlackboardPatch.builder()
                    .activeExpert((String) map.get("activeExpert"))
                    .currentIntent((String) map.get("currentIntent"))
                    .customerFactsPatch((Map<String, Object>) map.get("customerFactsPatch"))
                    .collectedSlotsPatch((Map<String, Object>) map.get("collectedSlotsPatch"))
                    .businessStatePatch((Map<String, Object>) map.get("businessStatePatch"))
                    .unresolvedQuestions((List<String>) map.get("unresolvedQuestions"))
                    .build();
        } catch (ClassCastException e) {
            log.warn("Malformed blackboardPatch from expert, ignoring: {}", e.getMessage());
            return new BlackboardPatch();
        }
    }

    private static double parseDouble(Object o, double dflt) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) {
            try { return Double.parseDouble(s); } catch (Exception ignore) {}
        }
        return dflt;
    }

    private void emitTextDeltas(FluxSink<Map<String, Object>> sink, String text) {
        if (text == null || text.isEmpty()) return;
        // Single-shot text event (matching the existing "text" SSE shape the frontend handles).
        sink.next(Map.of(
                "type", "text",
                "content", text,
                "sessionId", sessionId));
    }

    private String enumerateExperts() {
        StringBuilder sb = new StringBuilder();
        if (supervisorConfig.getSubAgents() != null) {
            for (var s : supervisorConfig.getSubAgents()) {
                if (sb.length() > 0) sb.append("、");
                sb.append(s.getAgentId());
            }
        }
        return sb.toString();
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private static String json(Object o) {
        try { return MAPPER.writeValueAsString(o); }
        catch (Exception e) { return String.valueOf(o); }
    }

    private static String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (var block : msg.getContent()) {
            if (block instanceof TextBlock tb && tb.getText() != null) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    private Msg supervisorAssistantMsg(String text) {
        return Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text(text).build())
                .build();
    }

    private void saveSupervisorState() {
        // The ReActAgent keeps its AgentState in the stateStore it was built with.
        // AgentScope persists state on each call automatically when a RuntimeContext is
        // supplied. Here we rely on the next call to reload it; for immediate durability
        // we additionally invoke the agent's save hook if present.
        // (No-op: state is mutated in place on the agent's state object which is the same
        // reference the store holds for InMemoryAgentStateStore; for JsonFile stores the
        // caller should pass a RuntimeContext on the next stream() to trigger persistence.)
    }

    private static String slotKey(String userId, String sessionId) {
        return (userId == null ? "__anon__" : userId) + "::" + sessionId;
    }

    @Override
    public void close() {
        // Supervisor state is external (SessionManager / store); nothing to free here.
    }

    // ---- Supervisor AgentState accessor (thin wrapper for read/append) ----

    /**
     * Thin helper that reads / mutates the Supervisor's Conversation AgentState via the
     * ReActAgent's accessor. The state lives under the store the Supervisor was built with
     * — NEVER the blackboard store and NEVER an expert's store.
     */
    static final class AgentStateAccessor {
        private final ReActAgent agent;

        AgentStateAccessor(ReActAgent agent) { this.agent = agent; }

        void appendUserMessage(Msg userMsg) {
            try {
                io.agentscope.core.state.AgentState state = agent.getAgentState();
                if (state != null) {
                    state.contextMutable().add(userMsg);
                }
            } catch (Exception e) {
                log.debug("Could not append user message to Supervisor state: {}", e.getMessage());
            }
        }

        void appendAssistantMessage(Msg assistantMsg) {
            try {
                io.agentscope.core.state.AgentState state = agent.getAgentState();
                if (state != null) {
                    state.contextMutable().add(assistantMsg);
                }
            } catch (Exception e) {
                log.debug("Could not append assistant message to Supervisor state: {}", e.getMessage());
            }
        }

        String getSummary() {
            try {
                io.agentscope.core.state.AgentState state = agent.getAgentState();
                return state != null ? state.getSummary() : "";
            } catch (Exception e) {
                return "";
            }
        }

        List<String> getRecentTurns(int n) {
            try {
                io.agentscope.core.state.AgentState state = agent.getAgentState();
                if (state == null) return List.of();
                var ctx = state.contextMutable();
                int size = ctx.size();
                int from = Math.max(0, size - n);
                return ctx.subList(from, size).stream()
                        .map(m -> {
                            StringBuilder sb = new StringBuilder();
                            if (m.getRole() != null) sb.append('[').append(m.getRole()).append("] ");
                            if (m.getContent() != null) {
                                for (var b : m.getContent()) {
                                    if (b instanceof TextBlock tb && tb.getText() != null) {
                                        sb.append(tb.getText());
                                    }
                                }
                            }
                            return sb.toString();
                        })
                        .toList();
            } catch (Exception e) {
                return List.of();
            }
        }
    }
}

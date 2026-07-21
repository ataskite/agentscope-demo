package com.skloda.agentscope.blackboard;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.agentscope.core.state.State;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Cross-expert shared business state for one {@code (userId, sessionId)} slot.
 *
 * <p>This is a project-level construct layered on top of AgentScope 2.0 GA's
 * {@link io.agentscope.core.state.AgentStateStore}. It is persisted under the
 * key {@code "shared_blackboard"} (configurable via
 * {@link com.skloda.agentscope.agent.AgentConfig.SharedBlackboardConfig#getStorageKey()})
 * — never under {@code "agent_state"}, which is reserved for the Supervisor's
 * Conversation AgentState.
 *
 * <p><b>Boundary rules enforced by the runtime:</b>
 * <ul>
 *   <li>The Supervisor is the only writer. Experts return {@link BlackboardPatch}
 *       payloads; the Supervisor validates and merges them.</li>
 *   <li>All mutable collections returned from accessors are defensive copies —
 *       callers cannot mutate the blackboard's internal state by holding a
 *       reference returned from a getter.</li>
 *   <li>{@code version} is incremented on every successful patch. Patches must
 *       be applied serially per session (see {@link BlackboardService}).</li>
 * </ul>
 *
 * <p>AgentScope 2.0 GA does not provide a native SharedBlackboard type. This
 * class is a thin domain object implementing the {@link State} marker so it can
 * be stored via the standard {@code AgentStateStore} SPI.
 */
@JsonPropertyOrder({
        "version", "activeExpert", "currentIntent", "customerFacts",
        "collectedSlots", "businessState", "findings", "unresolvedQuestions",
        "createdAt", "updatedAt"
})
public final class SessionBlackboard implements State {

    private long version;
    private String activeExpert;
    private String currentIntent;
    private final Map<String, Object> customerFacts;
    private final Map<String, Object> collectedSlots;
    private final Map<String, Object> businessState;
    private final List<ExpertFinding> findings;
    private final List<String> unresolvedQuestions;
    private final long createdAt;
    private long updatedAt;

    public SessionBlackboard() {
        this(0, null, null, new LinkedHashMap<>(), new LinkedHashMap<>(),
                new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>(),
                System.currentTimeMillis(), System.currentTimeMillis());
    }

    @JsonCreator
    public SessionBlackboard(
            @JsonProperty("version") long version,
            @JsonProperty("activeExpert") String activeExpert,
            @JsonProperty("currentIntent") String currentIntent,
            @JsonProperty("customerFacts") Map<String, Object> customerFacts,
            @JsonProperty("collectedSlots") Map<String, Object> collectedSlots,
            @JsonProperty("businessState") Map<String, Object> businessState,
            @JsonProperty("findings") List<ExpertFinding> findings,
            @JsonProperty("unresolvedQuestions") List<String> unresolvedQuestions,
            @JsonProperty("createdAt") long createdAt,
            @JsonProperty("updatedAt") long updatedAt) {
        this.version = version;
        this.activeExpert = activeExpert;
        this.currentIntent = currentIntent;
        this.customerFacts = customerFacts != null ? new LinkedHashMap<>(customerFacts) : new LinkedHashMap<>();
        this.collectedSlots = collectedSlots != null ? new LinkedHashMap<>(collectedSlots) : new LinkedHashMap<>();
        this.businessState = businessState != null ? new LinkedHashMap<>(businessState) : new LinkedHashMap<>();
        this.findings = findings != null ? new ArrayList<>(findings) : new ArrayList<>();
        this.unresolvedQuestions =
                unresolvedQuestions != null ? new ArrayList<>(unresolvedQuestions) : new ArrayList<>();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getVersion() { return version; }
    public String getActiveExpert() { return activeExpert; }
    public String getCurrentIntent() { return currentIntent; }

    /** Defensive copy — mutating the returned map does not change the blackboard. */
    public Map<String, Object> getCustomerFacts() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(customerFacts));
    }

    /** Defensive copy. */
    public Map<String, Object> getCollectedSlots() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(collectedSlots));
    }

    /** Defensive copy. */
    public Map<String, Object> getBusinessState() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(businessState));
    }

    /** Defensive copy of findings list; each {@link ExpertFinding} is itself immutable. */
    public List<ExpertFinding> getFindings() {
        return Collections.unmodifiableList(new ArrayList<>(findings));
    }

    /** Defensive copy. */
    public List<String> getUnresolvedQuestions() {
        return Collections.unmodifiableList(new ArrayList<>(unresolvedQuestions));
    }

    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }

    /**
     * Produce a deep defensive snapshot suitable for handing to an expert as read-only input.
     * Experts must not be able to reach our internal collections.
     */
    public SessionBlackboard snapshot() {
        return new SessionBlackboard(
                version, activeExpert, currentIntent,
                customerFacts, collectedSlots, businessState,
                findings, unresolvedQuestions, createdAt, updatedAt);
    }

    /**
     * Apply a validated {@link BlackboardPatch} in place. Visible only to package — only
     * {@link BlackboardService} (the Supervisor's write facade) calls this under the
     * per-session lock, so we guarantee serial application and a monotonic version.
     */
    void applyPatch(BlackboardPatch patch) {
        Objects.requireNonNull(patch, "patch");
        // Merge three business maps (shallow merge — expert is responsible for value shape).
        patchMerge(customerFacts, patch.getCustomerFactsPatch());
        patchMerge(collectedSlots, patch.getCollectedSlotsPatch());
        patchMerge(businessState, patch.getBusinessStatePatch());

        if (patch.getFindingsToAdd() != null && !patch.getFindingsToAdd().isEmpty()) {
            findings.addAll(patch.getFindingsToAdd());
        }
        if (patch.getUnresolvedQuestions() != null) {
            unresolvedQuestions.clear();
            unresolvedQuestions.addAll(patch.getUnresolvedQuestions());
        }
        if (patch.getActiveExpert() != null) {
            this.activeExpert = patch.getActiveExpert();
        }
        if (patch.getCurrentIntent() != null) {
            this.currentIntent = patch.getCurrentIntent();
        }
        this.version += 1;
        this.updatedAt = System.currentTimeMillis();
    }

    private static void patchMerge(Map<String, Object> target, Map<String, Object> src) {
        if (src == null || src.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> e : src.entrySet()) {
            if (e.getValue() == null) {
                target.remove(e.getKey());
            } else {
                target.put(e.getKey(), e.getValue());
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionBlackboard that)) return false;
        return version == that.version
                && Objects.equals(activeExpert, that.activeExpert)
                && Objects.equals(currentIntent, that.currentIntent)
                && Objects.equals(customerFacts, that.customerFacts)
                && Objects.equals(collectedSlots, that.collectedSlots)
                && Objects.equals(businessState, that.businessState)
                && Objects.equals(findings, that.findings)
                && Objects.equals(unresolvedQuestions, that.unresolvedQuestions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, activeExpert, currentIntent, customerFacts,
                collectedSlots, businessState, findings, unresolvedQuestions);
    }

    @Override
    public String toString() {
        return "SessionBlackboard{version=" + version
                + ", activeExpert='" + activeExpert + '\''
                + ", currentIntent='" + currentIntent + '\''
                + ", facts=" + customerFacts.size()
                + ", slots=" + collectedSlots.size()
                + ", findings=" + findings.size()
                + ", unresolved=" + unresolvedQuestions.size() + '}';
    }
}

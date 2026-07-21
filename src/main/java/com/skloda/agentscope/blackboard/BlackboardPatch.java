package com.skloda.agentscope.blackboard;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Proposed mutation to a {@link SessionBlackboard}, produced by an expert and applied by the
 * Supervisor (the sole writer).
 *
 * <p>Semantics:
 * <ul>
 *   <li>Any of {@code activeExpert}, {@code currentIntent} set to non-null replaces the field.</li>
 *   <li>Each {@code *Patch} map is merged into the corresponding blackboard map.
 *       A {@code null} value for an existing key <em>removes</em> that key (deletion).</li>
 *   <li>{@code findingsToAdd} is appended to the findings audit trail.</li>
 *   <li>{@code unresolvedQuestions}, if non-null, fully replaces the list.</li>
 * </ul>
 *
 * <p>This object is immutable; its maps are unmodifiable views over the data supplied at
 * construction. Patches are applied by {@link BlackboardService} under the per-session lock.
 */
public final class BlackboardPatch {

    private final String activeExpert;
    private final String currentIntent;
    private final Map<String, Object> customerFactsPatch;
    private final Map<String, Object> collectedSlotsPatch;
    private final Map<String, Object> businessStatePatch;
    private final List<ExpertFinding> findingsToAdd;
    private final List<String> unresolvedQuestions;

    public BlackboardPatch() {
        this(null, null, null, null, null, null, null);
    }

    @JsonCreator
    public BlackboardPatch(
            @JsonProperty("activeExpert") String activeExpert,
            @JsonProperty("currentIntent") String currentIntent,
            @JsonProperty("customerFactsPatch") Map<String, Object> customerFactsPatch,
            @JsonProperty("collectedSlotsPatch") Map<String, Object> collectedSlotsPatch,
            @JsonProperty("businessStatePatch") Map<String, Object> businessStatePatch,
            @JsonProperty("findingsToAdd") List<ExpertFinding> findingsToAdd,
            @JsonProperty("unresolvedQuestions") List<String> unresolvedQuestions) {
        this.activeExpert = activeExpert;
        this.currentIntent = currentIntent;
        this.customerFactsPatch = unmodifiable(customerFactsPatch);
        this.collectedSlotsPatch = unmodifiable(collectedSlotsPatch);
        this.businessStatePatch = unmodifiable(businessStatePatch);
        this.findingsToAdd = findingsToAdd != null
                ? Collections.unmodifiableList(new ArrayList<>(findingsToAdd))
                : Collections.emptyList();
        this.unresolvedQuestions = unresolvedQuestions != null
                ? Collections.unmodifiableList(new ArrayList<>(unresolvedQuestions))
                : null; // null means "leave unchanged" (distinct from empty list = clear all)
    }

    private static Map<String, Object> unmodifiable(Map<String, Object> src) {
        if (src == null || src.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(src));
    }

    public String getActiveExpert() { return activeExpert; }
    public String getCurrentIntent() { return currentIntent; }
    public Map<String, Object> getCustomerFactsPatch() { return customerFactsPatch; }
    public Map<String, Object> getCollectedSlotsPatch() { return collectedSlotsPatch; }
    public Map<String, Object> getBusinessStatePatch() { return businessStatePatch; }
    public List<ExpertFinding> getFindingsToAdd() { return findingsToAdd; }

    /** @return null = leave the existing list unchanged; empty list = clear. */
    public List<String> getUnresolvedQuestions() { return unresolvedQuestions; }

    public boolean isEmpty() {
        return activeExpert == null
                && currentIntent == null
                && customerFactsPatch.isEmpty()
                && collectedSlotsPatch.isEmpty()
                && businessStatePatch.isEmpty()
                && findingsToAdd.isEmpty()
                && unresolvedQuestions == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlackboardPatch that)) return false;
        return Objects.equals(activeExpert, that.activeExpert)
                && Objects.equals(currentIntent, that.currentIntent)
                && Objects.equals(customerFactsPatch, that.customerFactsPatch)
                && Objects.equals(collectedSlotsPatch, that.collectedSlotsPatch)
                && Objects.equals(businessStatePatch, that.businessStatePatch)
                && Objects.equals(findingsToAdd, that.findingsToAdd)
                && Objects.equals(unresolvedQuestions, that.unresolvedQuestions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(activeExpert, currentIntent, customerFactsPatch,
                collectedSlotsPatch, businessStatePatch, findingsToAdd, unresolvedQuestions);
    }

    @Override
    public String toString() {
        return "BlackboardPatch{activeExpert='" + activeExpert + '\''
                + ", intent='" + currentIntent + '\''
                + ", factsPatch=" + customerFactsPatch.size()
                + ", slotsPatch=" + collectedSlotsPatch.size()
                + ", statePatch=" + businessStatePatch.size()
                + ", findingsAdd=" + findingsToAdd.size() + '}';
    }

    /** Convenience builder (BlackboardPatch is immutable). */
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String activeExpert;
        private String currentIntent;
        private Map<String, Object> customerFactsPatch;
        private Map<String, Object> collectedSlotsPatch;
        private Map<String, Object> businessStatePatch;
        private List<ExpertFinding> findingsToAdd;
        private List<String> unresolvedQuestions;

        public Builder activeExpert(String e) { this.activeExpert = e; return this; }
        public Builder currentIntent(String i) { this.currentIntent = i; return this; }
        public Builder customerFactsPatch(Map<String, Object> m) { this.customerFactsPatch = m; return this; }
        public Builder collectedSlotsPatch(Map<String, Object> m) { this.collectedSlotsPatch = m; return this; }
        public Builder businessStatePatch(Map<String, Object> m) { this.businessStatePatch = m; return this; }
        public Builder findingsToAdd(List<ExpertFinding> f) { this.findingsToAdd = f; return this; }
        public Builder unresolvedQuestions(List<String> q) { this.unresolvedQuestions = q; return this; }

        public BlackboardPatch build() {
            return new BlackboardPatch(activeExpert, currentIntent, customerFactsPatch,
                    collectedSlotsPatch, businessStatePatch, findingsToAdd, unresolvedQuestions);
        }
    }
}

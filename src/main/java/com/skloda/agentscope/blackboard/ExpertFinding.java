package com.skloda.agentscope.blackboard;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Immutable record of a single fact / conclusion contributed by one expert.
 *
 * <p>Stored inside {@link SessionBlackboard#getFindings()} as an append-only audit trail.
 * The mutable, query-able business facts themselves live in
 * {@code SessionBlackboard.customerFacts} / {@code collectedSlots} / {@code businessState};
 * this class is the narrative record of who added what and why.
 */
public final class ExpertFinding {

    private final String expertId;
    private final String key;
    private final Object value;
    private final String rationale;
    private final long timestamp;

    public ExpertFinding(String expertId, String key, Object value, String rationale) {
        this(expertId, key, value, rationale, System.currentTimeMillis());
    }

    @JsonCreator
    public ExpertFinding(
            @JsonProperty("expertId") String expertId,
            @JsonProperty("key") String key,
            @JsonProperty("value") Object value,
            @JsonProperty("rationale") String rationale,
            @JsonProperty("timestamp") long timestamp) {
        this.expertId = expertId;
        this.key = key;
        this.value = value;
        this.rationale = rationale;
        this.timestamp = timestamp;
    }

    public String getExpertId() { return expertId; }
    public String getKey() { return key; }
    public Object getValue() { return value; }
    public String getRationale() { return rationale; }
    public long getTimestamp() { return timestamp; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpertFinding that)) return false;
        return timestamp == that.timestamp
                && Objects.equals(expertId, that.expertId)
                && Objects.equals(key, that.key)
                && Objects.equals(value, that.value)
                && Objects.equals(rationale, that.rationale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expertId, key, value, rationale, timestamp);
    }

    @Override
    public String toString() {
        return "ExpertFinding{" + expertId + "::" + key + "=" + value + '}';
    }
}

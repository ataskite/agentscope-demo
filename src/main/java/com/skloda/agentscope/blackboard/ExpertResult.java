package com.skloda.agentscope.blackboard;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Structured output an expert MUST return to the Supervisor.
 *
 * <p>The Supervisor is the only writer of the blackboard — it inspects
 * {@link #blackboardPatch}, optionally validates it, and calls
 * {@link BlackboardService#applyPatch}. The expert has no direct write path.
 *
 * <p>Required behavior contract (objective §Required behavior #4):
 * <ul>
 *   <li>{@code expertId} — identifies which expert answered.</li>
 *   <li>{@code answer} — final user-facing text.</li>
 *   <li>{@code confidence} — 0..1 self-reported; informs KEEP/SWITCH/CLARIFY.</li>
 *   <li>{@code blackboardPatch} — may be empty if expert has nothing to add.</li>
 *   <li>{@code unresolvedQuestions} — open questions to surface to the user.</li>
 * </ul>
 */
public final class ExpertResult {

    private final String expertId;
    private final String answer;
    private final double confidence;
    private final BlackboardPatch blackboardPatch;
    private final List<String> unresolvedQuestions;

    public ExpertResult(String expertId, String answer) {
        this(expertId, answer, 0.8, new BlackboardPatch(), List.of());
    }

    @JsonCreator
    public ExpertResult(
            @JsonProperty("expertId") String expertId,
            @JsonProperty("answer") String answer,
            @JsonProperty("confidence") double confidence,
            @JsonProperty("blackboardPatch") BlackboardPatch blackboardPatch,
            @JsonProperty("unresolvedQuestions") List<String> unresolvedQuestions) {
        this.expertId = Objects.requireNonNull(expertId, "expertId");
        this.answer = answer == null ? "" : answer;
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        this.blackboardPatch = blackboardPatch != null ? blackboardPatch : new BlackboardPatch();
        this.unresolvedQuestions = unresolvedQuestions == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(unresolvedQuestions));
    }

    public String getExpertId() { return expertId; }
    public String getAnswer() { return answer; }
    public double getConfidence() { return confidence; }
    public BlackboardPatch getBlackboardPatch() { return blackboardPatch; }
    public List<String> getUnresolvedQuestions() { return unresolvedQuestions; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpertResult that)) return false;
        return Double.compare(confidence, that.confidence) == 0
                && Objects.equals(expertId, that.expertId)
                && Objects.equals(answer, that.answer)
                && Objects.equals(blackboardPatch, that.blackboardPatch)
                && Objects.equals(unresolvedQuestions, that.unresolvedQuestions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expertId, answer, confidence, blackboardPatch, unresolvedQuestions);
    }
}

package com.skloda.agentscope.blackboard;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic input handed by the Supervisor to an expert on every dispatch.
 *
 * <p>The Supervisor MUST build this object explicitly — it cannot rely on the LLM "remembering"
 * to include blackboard context in its message. The blackboard snapshot, conversation summary,
 * and routing rationale are passed as structured fields so the expert always sees them.
 */
public final class ExpertRequest {

    private final String expertId;
    private final String currentMessage;
    private final String conversationSummary;
    private final List<String> recentTurns;
    private final SessionBlackboard blackboardSnapshot;
    private final String routingReason;
    private final String previousExpert;

    @JsonCreator
    public ExpertRequest(
            @JsonProperty("expertId") String expertId,
            @JsonProperty("currentMessage") String currentMessage,
            @JsonProperty("conversationSummary") String conversationSummary,
            @JsonProperty("recentTurns") List<String> recentTurns,
            @JsonProperty("blackboardSnapshot") SessionBlackboard blackboardSnapshot,
            @JsonProperty("routingReason") String routingReason,
            @JsonProperty("previousExpert") String previousExpert) {
        this.expertId = expertId;
        this.currentMessage = currentMessage;
        this.conversationSummary = conversationSummary;
        this.recentTurns = recentTurns != null ? List.copyOf(recentTurns) : List.of();
        // SessionBlackboard is already a defensive snapshot; keep as-is.
        this.blackboardSnapshot = blackboardSnapshot;
        this.routingReason = routingReason;
        this.previousExpert = previousExpert;
    }

    public String getExpertId() { return expertId; }
    public String getCurrentMessage() { return currentMessage; }
    public String getConversationSummary() { return conversationSummary; }
    public List<String> getRecentTurns() { return recentTurns; }
    public SessionBlackboard getBlackboardSnapshot() { return blackboardSnapshot; }
    public String getRoutingReason() { return routingReason; }
    public String getPreviousExpert() { return previousExpert; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpertRequest that)) return false;
        return Objects.equals(expertId, that.expertId)
                && Objects.equals(currentMessage, that.currentMessage)
                && Objects.equals(conversationSummary, that.conversationSummary)
                && Objects.equals(recentTurns, that.recentTurns)
                && Objects.equals(blackboardSnapshot, that.blackboardSnapshot)
                && Objects.equals(routingReason, that.routingReason)
                && Objects.equals(previousExpert, that.previousExpert);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expertId, currentMessage, conversationSummary, recentTurns,
                blackboardSnapshot, routingReason, previousExpert);
    }
}

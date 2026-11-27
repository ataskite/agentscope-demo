package com.skloda.agentscope.service;

import com.skloda.agentscope.model.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P3-4 / P2-1: per-agent transcript cap enforcement (ring truncation) and the new
 * cheap count API (P2-10).
 */
class ChatHistoryTruncationRegressionTest {

    @Test
    void transcriptIsTruncatedToMaxHistory() {
        InMemoryChatHistoryRepository repository = new InMemoryChatHistoryRepository(5);
        for (int i = 0; i < 12; i++) {
            repository.append("agent-a", ChatMessage.user("message-" + i));
        }

        List<ChatMessage> messages = repository.findByAgentId("agent-a");
        assertEquals(5, messages.size(), "Transcript must be capped at max-history");
        // Only the most recent messages survive
        assertEquals("message-7", messages.get(0).getContent());
        assertEquals("message-11", messages.get(4).getContent());
    }

    @Test
    void countMatchesStoredSizeWithoutDeepCopy() {
        InMemoryChatHistoryRepository repository = new InMemoryChatHistoryRepository(100);
        repository.append("agent-b", ChatMessage.user("hello"));
        repository.append("agent-b", ChatMessage.user("world"));

        assertEquals(2, repository.countByAgentId("agent-b"));
        assertEquals(0, repository.countByAgentId("agent-unknown"));
        assertEquals(0, repository.countByAgentId(null));
    }

    @Test
    void disabledCapKeepsFullTranscript() {
        InMemoryChatHistoryRepository repository = new InMemoryChatHistoryRepository(0);
        for (int i = 0; i < 8; i++) {
            repository.append("agent-c", ChatMessage.user("m" + i));
        }
        assertEquals(8, repository.countByAgentId("agent-c"));
    }
}

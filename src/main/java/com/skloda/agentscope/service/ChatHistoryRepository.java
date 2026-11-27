package com.skloda.agentscope.service;

import com.skloda.agentscope.model.ChatMessage;

import java.util.List;

/**
 * Storage boundary for chat transcripts.
 *
 * <p>Swap this interface to Redis, JDBC, or another store without changing
 * controller/frontend contracts.
 */
public interface ChatHistoryRepository {

    List<ChatMessage> findByAgentId(String agentId);

    /**
     * P2-10: cheap message count without materializing the full transcript.
     */
    int countByAgentId(String agentId);

    void append(String agentId, ChatMessage message);

    void clear(String agentId);
}

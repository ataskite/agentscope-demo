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

    void append(String agentId, ChatMessage message);

    void clear(String agentId);
}

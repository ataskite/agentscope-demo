package com.skloda.agentscope.service;

import com.skloda.agentscope.model.ChatMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class InMemoryChatHistoryRepository implements ChatHistoryRepository {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ChatMessage>> messagesByAgent =
            new ConcurrentHashMap<>();

    /**
     * P2-1: per-agent transcript cap (consumes {@code agentscope.memory.max-history},
     * previously declared in application.yml but never read by this implementation —
     * transcripts grew without bound). {@code <= 0} disables the cap.
     */
    private final int maxHistory;

    public InMemoryChatHistoryRepository(
            @Value("${agentscope.memory.max-history:100}") int maxHistory) {
        this.maxHistory = maxHistory;
    }

    /** Convenience constructor for tests (default cap 100). */
    public InMemoryChatHistoryRepository() {
        this(100);
    }

    @Override
    public List<ChatMessage> findByAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(messagesByAgent.getOrDefault(agentId, new CopyOnWriteArrayList<>())
                .stream()
                .map(ChatMessage::new)
                .toList());
    }

    @Override
    public int countByAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return 0;
        }
        CopyOnWriteArrayList<ChatMessage> list = messagesByAgent.get(agentId);
        return list != null ? list.size() : 0;
    }

    @Override
    public void append(String agentId, ChatMessage message) {
        if (agentId == null || agentId.isBlank() || message == null) {
            return;
        }
        ChatMessage stored = new ChatMessage(message);
        stored.setAgentId(agentId);
        CopyOnWriteArrayList<ChatMessage> list =
                messagesByAgent.computeIfAbsent(agentId, ignored -> new CopyOnWriteArrayList<>());
        list.add(stored);
        // P2-1: ring-style truncation keeps only the most recent maxHistory messages
        if (maxHistory > 0) {
            synchronized (list) {
                int excess = list.size() - maxHistory;
                if (excess > 0) {
                    list.subList(0, excess).clear();
                }
            }
        }
    }

    @Override
    public void clear(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return;
        }
        messagesByAgent.remove(agentId);
    }
}

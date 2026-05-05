package com.skloda.agentscope.service;

import com.skloda.agentscope.model.ChatMessage;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class InMemoryChatHistoryRepository implements ChatHistoryRepository {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ChatMessage>> messagesByAgent =
            new ConcurrentHashMap<>();

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
    public void append(String agentId, ChatMessage message) {
        if (agentId == null || agentId.isBlank() || message == null) {
            return;
        }
        ChatMessage stored = new ChatMessage(message);
        stored.setAgentId(agentId);
        messagesByAgent.computeIfAbsent(agentId, ignored -> new CopyOnWriteArrayList<>())
                .add(stored);
    }

    @Override
    public void clear(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return;
        }
        messagesByAgent.remove(agentId);
    }
}

package com.skloda.agentscope.service;

import com.skloda.agentscope.model.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryChatHistoryRepositoryTest {

    @Test
    void storesMessagesByAgentAndReturnsCopies() {
        ChatHistoryRepository repository = new InMemoryChatHistoryRepository();

        repository.append("chat-basic", ChatMessage.user("hello"));
        repository.append("chat-basic", ChatMessage.assistant("hi there"));
        repository.append("task-document-analysis", ChatMessage.user("summarize"));

        List<ChatMessage> chatBasic = repository.findByAgentId("chat-basic");
        chatBasic.clear();

        assertEquals(2, repository.findByAgentId("chat-basic").size());
        assertEquals(1, repository.findByAgentId("task-document-analysis").size());
        assertEquals("hi there", repository.findByAgentId("chat-basic").get(1).getContent());
    }

    @Test
    void clearsOnlyRequestedAgentHistory() {
        ChatHistoryRepository repository = new InMemoryChatHistoryRepository();
        repository.append("chat-basic", ChatMessage.user("hello"));
        repository.append("task-document-analysis", ChatMessage.user("summarize"));

        repository.clear("chat-basic");

        assertEquals(List.of(), repository.findByAgentId("chat-basic"));
        assertEquals(1, repository.findByAgentId("task-document-analysis").size());
    }
}

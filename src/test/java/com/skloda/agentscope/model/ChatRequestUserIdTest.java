package com.skloda.agentscope.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatRequestUserIdTest {

    @Test
    void userIdDefaultsToNull() {
        ChatRequest request = new ChatRequest();
        assertNull(request.getUserId());
    }

    @Test
    void userIdCanBeSetAndRetrieved() {
        ChatRequest request = new ChatRequest();
        request.setUserId("alice");
        assertEquals("alice", request.getUserId());
    }

    @Test
    void userIdCanBeCleared() {
        ChatRequest request = new ChatRequest();
        request.setUserId("bob");
        assertEquals("bob", request.getUserId());

        request.setUserId(null);
        assertNull(request.getUserId());
    }

    @Test
    void userIdCoexistsWithOtherFields() {
        ChatRequest request = new ChatRequest();
        request.setAgentId("finance-intel-tracker");
        request.setMessage("analyze banking trends");
        request.setSessionId("sess-123");
        request.setUserId("charlie");

        assertEquals("finance-intel-tracker", request.getAgentId());
        assertEquals("analyze banking trends", request.getMessage());
        assertEquals("sess-123", request.getSessionId());
        assertEquals("charlie", request.getUserId());
    }
}

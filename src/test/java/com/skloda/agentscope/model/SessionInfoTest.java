package com.skloda.agentscope.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionInfoTest {

    @Test
    void storesSessionListFields() {
        SessionInfo info = new SessionInfo();

        info.setSessionId("session-1");
        info.setAgentId("chat-basic");
        info.setAgentName("Basic Chat");
        info.setMessageCount(7);
        info.setCreatedAt("2026-04-29 01:00:00");
        info.setLastAccessedAt("2026-04-29 01:30:00");
        info.setPreview("hello");

        assertEquals("session-1", info.getSessionId());
        assertEquals("chat-basic", info.getAgentId());
        assertEquals("Basic Chat", info.getAgentName());
        assertEquals(7, info.getMessageCount());
        assertEquals("2026-04-29 01:00:00", info.getCreatedAt());
        assertEquals("2026-04-29 01:30:00", info.getLastAccessedAt());
        assertEquals("hello", info.getPreview());
    }
}

package com.skloda.agentscope.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatEventTest {

    @Test
    void doneEventOnlyContainsType() {
        ChatEvent event = ChatEvent.done();

        assertEquals("done", event.getType());
        assertNull(event.getMessage());
        assertNull(event.getContent());
    }

    @Test
    void errorEventContainsMessage() {
        ChatEvent event = ChatEvent.error("boom");

        assertEquals("error", event.getType());
        assertEquals("boom", event.getMessage());
        assertNull(event.getContent());
    }
}

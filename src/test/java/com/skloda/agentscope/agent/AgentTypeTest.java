package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentTypeTest {
    @Test
    void testEnumValues() {
        assertEquals(6, AgentType.values().length);
    }

    @Test
    void testDefaultIsSingle() {
        assertTrue(AgentType.SINGLE.isDefault());
        assertFalse(AgentType.SEQUENTIAL.isDefault());
    }
}

package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TriggerTypeTest {
    @Test
    void testEnumValues() {
        assertEquals(3, TriggerType.values().length);
    }

    @Test
    void testEnumDescriptions() {
        assertEquals("Intent-based trigger", TriggerType.INTENT.getDescription());
    }
}

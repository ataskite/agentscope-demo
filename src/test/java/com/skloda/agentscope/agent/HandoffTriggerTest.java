package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class HandoffTriggerTest {
    @Test
    void testBuilderPattern() {
        HandoffTrigger trigger = HandoffTrigger.builder()
            .type(TriggerType.EXPLICIT)
            .keywords(List.of("转人工", "客服"))
            .target("support-expert")
            .build();
        assertEquals(TriggerType.EXPLICIT, trigger.getType());
        assertEquals("support-expert", trigger.getTarget());
    }

    @Test
    void testMatchesWithKeyword() {
        HandoffTrigger trigger = HandoffTrigger.builder()
            .type(TriggerType.INTENT)
            .keywords(List.of("购买", "价格"))
            .target("sales-expert")
            .build();
        assertTrue(trigger.matches("我想购买产品"));
        assertTrue(trigger.matches("价格是多少"));
        assertFalse(trigger.matches("你好"));
    }
}

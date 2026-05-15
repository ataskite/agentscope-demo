package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StateConfigTest {

    @Test
    void constructor_createsEmptyConfig() {
        StateConfig config = new StateConfig();

        assertNotNull(config);
        assertNull(config.getName());
        assertNull(config.getAgent());
        assertNull(config.getTransitions());
    }

    @Test
    void builder_createsConfigWithAllValues() {
        StateTransition transition = StateTransition.builder()
                .target("DONE")
                .event("AUTO")
                .build();

        StateConfig config = StateConfig.builder()
                .name("CREATED")
                .agent("order-reviewer")
                .transitions(List.of(transition))
                .build();

        assertEquals("CREATED", config.getName());
        assertEquals("order-reviewer", config.getAgent());
        assertEquals(1, config.getTransitions().size());
        assertEquals("DONE", config.getTransitions().get(0).getTarget());
    }

    @Test
    void builder_withEmptyTransitions() {
        StateConfig config = StateConfig.builder()
                .name("PENDING")
                .agent("support-agent")
                .build();

        assertEquals("PENDING", config.getName());
        assertEquals("support-agent", config.getAgent());
        assertNull(config.getTransitions());
    }

    @Test
    void allArgsConstructor_createsConfigWithAllValues() {
        StateTransition transition = StateTransition.builder()
                .target("APPROVED")
                .event("APPROVE")
                .build();

        StateConfig config = new StateConfig("REVIEWING", "order-reviewer", List.of(transition));

        assertEquals("REVIEWING", config.getName());
        assertEquals("order-reviewer", config.getAgent());
        assertEquals(1, config.getTransitions().size());
        assertEquals("APPROVED", config.getTransitions().get(0).getTarget());
    }

    @Test
    void setters_canModifyValues() {
        StateConfig config = new StateConfig();
        StateTransition transition = StateTransition.builder()
                .target("PAID")
                .event("PAY")
                .build();

        config.setName("SUBMITTED");
        config.setAgent("payment-agent");
        config.setTransitions(List.of(transition));

        assertEquals("SUBMITTED", config.getName());
        assertEquals("payment-agent", config.getAgent());
        assertEquals(1, config.getTransitions().size());
    }

    @Test
    void multipleTransitions() {
        StateTransition t1 = StateTransition.builder().target("APPROVED").event("APPROVE").build();
        StateTransition t2 = StateTransition.builder().target("REJECTED").event("REJECT").build();

        StateConfig config = StateConfig.builder()
                .name("REVIEWING")
                .agent("order-reviewer")
                .transitions(List.of(t1, t2))
                .build();

        assertEquals(2, config.getTransitions().size());
        assertEquals("APPROVED", config.getTransitions().get(0).getTarget());
        assertEquals("REJECTED", config.getTransitions().get(1).getTarget());
    }
}

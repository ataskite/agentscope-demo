package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StateTransitionTest {

    @Test
    void constructor_createsEmptyTransition() {
        StateTransition transition = new StateTransition();

        assertNotNull(transition);
        assertNull(transition.getEvent());
        assertNull(transition.getCondition());
        assertNull(transition.getTarget());
    }

    @Test
    void builder_createsTransitionWithAllValues() {
        StateTransition transition = StateTransition.builder()
                .event("APPROVE")
                .condition("amount < 10000")
                .target("APPROVED")
                .build();

        assertEquals("APPROVE", transition.getEvent());
        assertEquals("amount < 10000", transition.getCondition());
        assertEquals("APPROVED", transition.getTarget());
    }

    @Test
    void builder_withOnlyTarget() {
        StateTransition transition = StateTransition.builder()
                .target("DONE")
                .build();

        assertNull(transition.getEvent());
        assertNull(transition.getCondition());
        assertEquals("DONE", transition.getTarget());
    }

    @Test
    void builder_withEventAndTarget() {
        StateTransition transition = StateTransition.builder()
                .event("SUBMIT")
                .target("SUBMITTED")
                .build();

        assertEquals("SUBMIT", transition.getEvent());
        assertNull(transition.getCondition());
        assertEquals("SUBMITTED", transition.getTarget());
    }

    @Test
    void allArgsConstructor_createsTransitionWithAllValues() {
        StateTransition transition = new StateTransition("PAY", "paid == true", "PAID");

        assertEquals("PAY", transition.getEvent());
        assertEquals("paid == true", transition.getCondition());
        assertEquals("PAID", transition.getTarget());
    }

    @Test
    void setters_canModifyValues() {
        StateTransition transition = new StateTransition();

        transition.setEvent("REJECT");
        transition.setCondition("has_errors == true");
        transition.setTarget("REJECTED");

        assertEquals("REJECT", transition.getEvent());
        assertEquals("has_errors == true", transition.getCondition());
        assertEquals("REJECTED", transition.getTarget());
    }

    @Test
    void autoTransition_withCondition() {
        StateTransition transition = StateTransition.builder()
                .condition("auto_approve == true")
                .target("APPROVED")
                .build();

        assertNull(transition.getEvent());
        assertEquals("auto_approve == true", transition.getCondition());
        assertEquals("APPROVED", transition.getTarget());
    }

    @Test
    void eventDrivenTransition_noCondition() {
        StateTransition transition = StateTransition.builder()
                .event("USER_ACTION")
                .target("PROCESSING")
                .build();

        assertEquals("USER_ACTION", transition.getEvent());
        assertNull(transition.getCondition());
        assertEquals("PROCESSING", transition.getTarget());
    }
}

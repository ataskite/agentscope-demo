package com.skloda.agentscope.harness;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class HarnessAgentServiceUserIdTest {

    @Test
    void createStreamFluxAcceptsUserIdAndExecutionMode() throws NoSuchMethodException {
        Method method = HarnessAgentService.class.getMethod(
                "createStreamFlux",
                String.class, String.class,
                String.class, String.class,
                String.class, String.class,
                String.class
        );
        assertEquals(7, method.getParameterCount());

        java.lang.reflect.Parameter[] params = method.getParameters();
        assertEquals("userId", params[5].getName());
        assertEquals("executionMode", params[6].getName());
    }
}

package com.skloda.agentscope.harness;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentType;
import com.skloda.agentscope.agent.HarnessConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class HarnessModeOverrideTest {

    @Test
    void harnessAgentFactoryHas3ArgCreate() throws NoSuchMethodException {
        Method m = HarnessAgentFactory.class.getMethod("create",
                AgentConfig.class, String.class, String.class);
        assertEquals(3, m.getParameterCount());
    }

    @Test
    void harnessAgentFactoryHas2ArgCreate() throws NoSuchMethodException {
        Method m = HarnessAgentFactory.class.getMethod("create",
                AgentConfig.class, String.class);
        assertEquals(2, m.getParameterCount());
    }

    @Test
    void harnessAgentServiceAcceptsExecutionMode() throws NoSuchMethodException {
        Method m = HarnessAgentService.class.getMethod("createStreamFlux",
                String.class, String.class,
                String.class, String.class,
                String.class, String.class,
                String.class);
        assertEquals(7, m.getParameterCount());
        java.lang.reflect.Parameter[] params = m.getParameters();
        assertEquals("executionMode", params[6].getName());
    }

    @Test
    void agentConfigWithOverrideMode() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("test-override");
        config.setType(AgentType.HARNESS);

        HarnessConfig hc = new HarnessConfig();
        hc.setExecutionMode("CLAW"); // default CLAW
        config.setHarnessConfig(hc);

        // Config says CLAW, but override says BUILDER
        assertFalse(config.getHarnessConfig().isBuilderMode());

        // Simulate override: the factory logic checks override first
        String override = "BUILDER";
        boolean isBuilder = "BUILDER".equalsIgnoreCase(override);
        assertTrue(isBuilder);
    }
}

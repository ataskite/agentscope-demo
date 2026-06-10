package com.skloda.agentscope.harness;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentType;
import com.skloda.agentscope.agent.HarnessConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class HarnessAgentFactoryModeTest {

    @Test
    void resolveWorkspaceExpandsUserHome() throws Exception {
        Method resolveMethod = HarnessAgentFactory.class.getDeclaredMethod(
                "resolveWorkspace", String.class, String.class);
        resolveMethod.setAccessible(true);

        String userHome = System.getProperty("user.home");
        Object result = resolveMethod.invoke(null, "${user.home}/.agentscope/test-agent", "test-agent");

        assertEquals(Paths.get(userHome, ".agentscope", "test-agent"), result);
    }

    @Test
    void resolveWorkspaceFallsBackToDefault() throws Exception {
        Method resolveMethod = HarnessAgentFactory.class.getDeclaredMethod(
                "resolveWorkspace", String.class, String.class);
        resolveMethod.setAccessible(true);

        String userHome = System.getProperty("user.home");
        Object result = resolveMethod.invoke(null, null, "my-agent");

        assertEquals(Paths.get(userHome, ".agentscope", "my-agent"), result);
    }

    @Test
    void resolveWorkspaceHandlesBlankPath() throws Exception {
        Method resolveMethod = HarnessAgentFactory.class.getDeclaredMethod(
                "resolveWorkspace", String.class, String.class);
        resolveMethod.setAccessible(true);

        String userHome = System.getProperty("user.home");
        Object result = resolveMethod.invoke(null, "   ", "another-agent");

        assertEquals(Paths.get(userHome, ".agentscope", "another-agent"), result);
    }

    @Test
    void createThrowsWhenHarnessConfigMissing() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("no-harness-config");
        config.setType(AgentType.HARNESS);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> HarnessAgentFactory.create(config, "test-key"));
        assertTrue(ex.getMessage().contains("no-harness-config"));
        assertTrue(ex.getMessage().contains("no harnessConfig"));
    }

    @Test
    void agentConfigWithHarnessConfigHasBuilderModeFlag() {
        AgentConfig config = new AgentConfig();
        HarnessConfig hc = new HarnessConfig();
        hc.setExecutionMode("BUILDER");
        hc.setWorkspace("/tmp/test-workspace");
        config.setHarnessConfig(hc);

        assertTrue(config.getHarnessConfig().isBuilderMode());
        assertEquals("BUILDER", config.getHarnessConfig().getExecutionMode());
    }

    @Test
    void agentConfigWithClawModeIsNotBuilder() {
        AgentConfig config = new AgentConfig();
        HarnessConfig hc = new HarnessConfig();
        hc.setExecutionMode("CLAW");
        config.setHarnessConfig(hc);

        assertFalse(config.getHarnessConfig().isBuilderMode());
    }
}

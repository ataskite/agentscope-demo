package com.skloda.agentscope.harness;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentType;
import com.skloda.agentscope.agent.HarnessConfig;
import com.skloda.agentscope.model.ModelFactory;
import com.skloda.agentscope.permission.PermissionContextFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class HarnessAgentFactoryModeTest {

    /**
     * Creates a HarnessAgentFactory instance with real FilesystemSpecFactory,
     * CompactionConfigFactory, and ModelFactory (all simple POJOs with no external
     * dependencies, safe to instantiate directly in unit tests).
     */
    private HarnessAgentFactory createFactory() {
        return new HarnessAgentFactory(
                new FilesystemSpecFactory(),
                new CompactionConfigFactory(),
                new ModelFactory(null),
                new PermissionContextFactory());
    }

    @Test
    void resolveWorkspaceExpandsUserHome() throws Exception {
        HarnessAgentFactory factory = createFactory();
        Method resolveMethod = HarnessAgentFactory.class.getDeclaredMethod(
                "resolveWorkspace", String.class, String.class);
        resolveMethod.setAccessible(true);

        String userHome = System.getProperty("user.home");
        Object result = resolveMethod.invoke(factory, "${user.home}/.agentscope/test-agent", "test-agent");

        assertEquals(Paths.get(userHome, ".agentscope", "test-agent"), result);
    }

    @Test
    void resolveWorkspaceFallsBackToDefault() throws Exception {
        HarnessAgentFactory factory = createFactory();
        Method resolveMethod = HarnessAgentFactory.class.getDeclaredMethod(
                "resolveWorkspace", String.class, String.class);
        resolveMethod.setAccessible(true);

        String userHome = System.getProperty("user.home");
        Object result = resolveMethod.invoke(factory, null, "my-agent");

        assertEquals(Paths.get(userHome, ".agentscope", "my-agent"), result);
    }

    @Test
    void resolveWorkspaceHandlesBlankPath() throws Exception {
        HarnessAgentFactory factory = createFactory();
        Method resolveMethod = HarnessAgentFactory.class.getDeclaredMethod(
                "resolveWorkspace", String.class, String.class);
        resolveMethod.setAccessible(true);

        String userHome = System.getProperty("user.home");
        Object result = resolveMethod.invoke(factory, "   ", "another-agent");

        assertEquals(Paths.get(userHome, ".agentscope", "another-agent"), result);
    }

    @Test
    void createThrowsWhenHarnessConfigMissing() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("no-harness-config");
        config.setType(AgentType.HARNESS);

        HarnessAgentFactory factory = createFactory();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.create(config, "test-key"));
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

    @Test
    void dockerModeDetectedFromFilesystemMode() {
        HarnessConfig hc = new HarnessConfig();
        hc.setFilesystemMode("DOCKER");
        assertTrue(hc.isDockerMode());

        hc.setFilesystemMode("LOCAL");
        assertFalse(hc.isDockerMode());
    }

    @Test
    void taskListEnabledDefaultsFalse() {
        HarnessConfig hc = new HarnessConfig();
        assertFalse(hc.isTaskListEnabled());

        hc.setTaskListEnabled(true);
        assertTrue(hc.isTaskListEnabled());
    }
}

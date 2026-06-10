package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that HarnessConfig is properly wired through AgentConfig
 * and that HARNESS type agents have their harnessConfig populated.
 */
class AgentConfigDualYamlTest {

    @Test
    void harnessConfigIsNullByDefault() {
        AgentConfig config = new AgentConfig();
        assertNull(config.getHarnessConfig());
    }

    @Test
    void harnessConfigCanBeSetOnAgentConfig() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("test-harness");
        config.setType(AgentType.HARNESS);

        HarnessConfig hc = new HarnessConfig();
        hc.setExecutionMode("BUILDER");
        hc.setWorkspace("/tmp/workspace");
        config.setHarnessConfig(hc);

        assertNotNull(config.getHarnessConfig());
        assertTrue(config.getHarnessConfig().isBuilderMode());
        assertEquals("/tmp/workspace", config.getHarnessConfig().getWorkspace());
    }

    @Test
    void harnessTypeIsDistinctFromOtherTypes() {
        assertNotEquals(AgentType.SINGLE, AgentType.HARNESS);
        assertNotEquals(AgentType.SEQUENTIAL, AgentType.HARNESS);
        assertNotEquals(AgentType.ROUTING, AgentType.HARNESS);
        assertNotEquals(AgentType.HANDOFFS, AgentType.HARNESS);
        assertNotEquals(AgentType.PARALLEL, AgentType.HARNESS);
    }

    @Test
    void harnessConfigWithClawModeAndSubagents() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("claw-agent");
        config.setType(AgentType.HARNESS);

        HarnessConfig hc = new HarnessConfig();
        hc.setExecutionMode("CLAW");
        hc.setFilesystemMode("LOCAL");

        HarnessConfig.SubAgentRef sub = new HarnessConfig.SubAgentRef();
        sub.setName("analyst");
        sub.setDescription("Data analyst");
        hc.setSubagents(java.util.List.of(sub));

        config.setHarnessConfig(hc);

        assertFalse(config.getHarnessConfig().isBuilderMode());
        assertEquals("LOCAL", config.getHarnessConfig().getFilesystemMode());
        assertEquals(1, config.getHarnessConfig().getSubagents().size());
        assertEquals("analyst", config.getHarnessConfig().getSubagents().get(0).getName());
    }

    @Test
    void harnessConfigWithBuilderModeAndIsolation() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("builder-agent");
        config.setType(AgentType.HARNESS);

        HarnessConfig hc = new HarnessConfig();
        hc.setExecutionMode("BUILDER");
        hc.setIsolationScope("USER");

        config.setHarnessConfig(hc);

        assertTrue(config.getHarnessConfig().isBuilderMode());
        assertEquals("USER", config.getHarnessConfig().getIsolationScope());
    }
}

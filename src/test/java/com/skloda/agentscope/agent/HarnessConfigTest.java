package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HarnessConfigTest {

    @Test
    void defaultsToClawMode() {
        HarnessConfig config = new HarnessConfig();
        assertEquals("CLAW", config.getExecutionMode());
        assertFalse(config.isBuilderMode());
    }

    @Test
    void builderModeEnabledWhenSet() {
        HarnessConfig config = new HarnessConfig();
        config.setExecutionMode("BUILDER");
        assertTrue(config.isBuilderMode());
        assertEquals("BUILDER", config.getExecutionMode());
    }

    @Test
    void executionModeCaseInsensitive() {
        HarnessConfig config = new HarnessConfig();
        config.setExecutionMode("builder");
        assertTrue(config.isBuilderMode());

        config.setExecutionMode("Builder");
        assertTrue(config.isBuilderMode());
    }

    @Test
    void defaultFilesystemModeIsLocal() {
        HarnessConfig config = new HarnessConfig();
        assertEquals("LOCAL", config.getFilesystemMode());
    }

    @Test
    void defaultIsolationScopeIsUser() {
        HarnessConfig config = new HarnessConfig();
        assertEquals("USER", config.getIsolationScope());
    }

    @Test
    void workspaceCanBeSetAndRetrieved() {
        HarnessConfig config = new HarnessConfig();
        config.setWorkspace("${user.home}/.agentscope/my-agent");
        assertEquals("${user.home}/.agentscope/my-agent", config.getWorkspace());
    }

    @Test
    void subagentsDefaultToEmptyList() {
        HarnessConfig config = new HarnessConfig();
        assertNotNull(config.getSubagents());
        assertTrue(config.getSubagents().isEmpty());
    }

    @Test
    void subagentsCanBeConfigured() {
        HarnessConfig config = new HarnessConfig();
        HarnessConfig.SubAgentRef ref = new HarnessConfig.SubAgentRef();
        ref.setName("analyst");
        ref.setDescription("Root cause analyst");
        config.setSubagents(List.of(ref));

        assertEquals(1, config.getSubagents().size());
        assertEquals("analyst", config.getSubagents().get(0).getName());
        assertEquals("Root cause analyst", config.getSubagents().get(0).getDescription());
    }

    @Test
    void compactionDefaults() {
        HarnessConfig.CompactionConfig cc = new HarnessConfig.CompactionConfig();
        assertEquals(30, cc.getTriggerMessages());
        assertEquals(10, cc.getKeepMessages());
        assertTrue(cc.isFlushBeforeCompact());
    }

    @Test
    void compactionCanBeCustomized() {
        HarnessConfig.CompactionConfig cc = new HarnessConfig.CompactionConfig();
        cc.setTriggerMessages(50);
        cc.setKeepMessages(20);
        cc.setFlushBeforeCompact(false);

        assertEquals(50, cc.getTriggerMessages());
        assertEquals(20, cc.getKeepMessages());
        assertFalse(cc.isFlushBeforeCompact());
    }

    @Test
    void subAgentRefGettersSetters() {
        HarnessConfig.SubAgentRef ref = new HarnessConfig.SubAgentRef();
        assertNull(ref.getName());
        assertNull(ref.getDescription());

        ref.setName("trend-analyst");
        ref.setDescription("Cross-day trend comparison");
        assertEquals("trend-analyst", ref.getName());
        assertEquals("Cross-day trend comparison", ref.getDescription());
    }
}

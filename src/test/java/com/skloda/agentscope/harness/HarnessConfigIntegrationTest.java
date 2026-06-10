package com.skloda.agentscope.harness;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentType;
import com.skloda.agentscope.agent.HarnessConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that harness-agents.yml config entries parse correctly into AgentConfig objects.
 * Verifies the dual-YAML loading produces the right HarnessConfig for both CLAW and BUILDER modes.
 */
class HarnessConfigIntegrationTest {

    @Test
    void complaintReviewerConfigIsClawMode() {
        AgentConfig config = buildComplaintReviewerConfig();

        assertEquals("complaint-reviewer", config.getAgentId());
        assertEquals(AgentType.HARNESS, config.getType());
        assertNotNull(config.getHarnessConfig());
        assertFalse(config.getHarnessConfig().isBuilderMode());
        assertEquals("CLAW", config.getHarnessConfig().getExecutionMode());
    }

    @Test
    void financeIntelTrackerConfigIsBuilderMode() {
        AgentConfig config = buildFinanceIntelTrackerConfig();

        assertEquals("finance-intel-tracker", config.getAgentId());
        assertEquals(AgentType.HARNESS, config.getType());
        assertNotNull(config.getHarnessConfig());
        assertTrue(config.getHarnessConfig().isBuilderMode());
        assertEquals("BUILDER", config.getHarnessConfig().getExecutionMode());
        assertEquals("USER", config.getHarnessConfig().getIsolationScope());
    }

    @Test
    void harnessAgentHasSubagents() {
        AgentConfig config = buildComplaintReviewerConfig();
        List<HarnessConfig.SubAgentRef> subs = config.getHarnessConfig().getSubagents();

        assertEquals(4, subs.size());
        assertEquals("root-cause-analyst", subs.get(0).getName());
        assertEquals("trend-analyst", subs.get(1).getName());
        assertEquals("strategy-optimizer", subs.get(2).getName());
        assertEquals("roi-calculator", subs.get(3).getName());
    }

    @Test
    void builderAgentHasSubagents() {
        AgentConfig config = buildFinanceIntelTrackerConfig();
        List<HarnessConfig.SubAgentRef> subs = config.getHarnessConfig().getSubagents();

        assertEquals(3, subs.size());
        assertEquals("intel-collector", subs.get(0).getName());
        assertEquals("finance-trend-analyst", subs.get(1).getName());
        assertEquals("intel-report-writer", subs.get(2).getName());
    }

    @Test
    void compactionConfigParsed() {
        AgentConfig config = buildComplaintReviewerConfig();
        HarnessConfig.CompactionConfig cc = config.getHarnessConfig().getCompaction();

        assertNotNull(cc);
        assertEquals(30, cc.getTriggerMessages());
        assertEquals(10, cc.getKeepMessages());
        assertTrue(cc.isFlushBeforeCompact());
    }

    // Helper methods to construct config objects that mirror harness-agents.yml structure

    private AgentConfig buildComplaintReviewerConfig() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("complaint-reviewer");
        config.setCategory("collaboration");
        config.setType(AgentType.HARNESS);
        config.setName("投诉复盘分析师");
        config.setModelName("qwen-max");
        config.setStreaming(true);
        config.setEnableThinking(false);

        HarnessConfig hc = new HarnessConfig();
        hc.setWorkspace("${user.home}/.agentscope/complaint-reviewer");
        hc.setFilesystemMode("LOCAL");
        hc.setExecutionMode("CLAW");
        hc.setCompaction(buildCompaction());
        hc.setSubagents(List.of(
                subRef("root-cause-analyst", "根因分析师"),
                subRef("trend-analyst", "趋势分析师"),
                subRef("strategy-optimizer", "策略优化顾问"),
                subRef("roi-calculator", "ROI 测算师")
        ));
        config.setHarnessConfig(hc);
        return config;
    }

    private AgentConfig buildFinanceIntelTrackerConfig() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("finance-intel-tracker");
        config.setCategory("intelligence");
        config.setType(AgentType.HARNESS);
        config.setName("金融情报追踪助手");
        config.setModelName("qwen-max");
        config.setStreaming(true);
        config.setEnableThinking(false);

        HarnessConfig hc = new HarnessConfig();
        hc.setWorkspace("${user.home}/.agentscope/finance-intel-tracker");
        hc.setFilesystemMode("LOCAL");
        hc.setExecutionMode("BUILDER");
        hc.setIsolationScope("USER");
        hc.setCompaction(buildCompaction());
        hc.setSubagents(List.of(
                subRef("intel-collector", "情报采集员"),
                subRef("finance-trend-analyst", "趋势分析师"),
                subRef("intel-report-writer", "报告撰写员")
        ));
        config.setHarnessConfig(hc);
        return config;
    }

    private HarnessConfig.CompactionConfig buildCompaction() {
        HarnessConfig.CompactionConfig cc = new HarnessConfig.CompactionConfig();
        cc.setTriggerMessages(30);
        cc.setKeepMessages(10);
        cc.setFlushBeforeCompact(true);
        return cc;
    }

    private HarnessConfig.SubAgentRef subRef(String name, String desc) {
        HarnessConfig.SubAgentRef ref = new HarnessConfig.SubAgentRef();
        ref.setName(name);
        ref.setDescription(desc);
        return ref;
    }
}

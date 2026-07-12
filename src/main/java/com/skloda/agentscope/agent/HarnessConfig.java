package com.skloda.agentscope.agent;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class HarnessConfig {

    private String workspace;
    private String filesystemMode = "LOCAL";
    private String executionMode = "CLAW";
    private String isolationScope = "USER";
    private CompactionConfig compaction;
    private List<SubAgentRef> subagents = new ArrayList<>();
    private boolean taskListEnabled = false;
    private SandboxConfig sandbox;

    // S6: Permission + Skill
    private AgentConfig.PermissionConfig permissionConfig;
    private String skillPath;

    // S7: Context control
    private List<String> additionalContextFiles = new ArrayList<>();
    private int maxContextTokens = 8000;
    private boolean metaToolEnabled = false;

    // S4: Plan Mode
    private PlanConfig plan;

    // S5: Layered Memory
    private MemoryConfig memory;

    // S10: Skill self-learning
    private SkillLearningConfig skillLearning;

    public boolean isBuilderMode() {
        return "BUILDER".equalsIgnoreCase(executionMode);
    }

    public boolean isDockerMode() {
        return "DOCKER".equalsIgnoreCase(filesystemMode);
    }

    @Setter
    @Getter
    public static class CompactionConfig {
        private int triggerMessages = 30;
        private int keepMessages = 10;
        private boolean flushBeforeCompact = true;
    }

    @Setter
    @Getter
    public static class SubAgentRef {
        private String name;
        private String description;
    }

    @Setter
    @Getter
    public static class SandboxConfig {
        private String image = "python:3.11-slim";
        private long memorySizeBytes = 2_000_000_000L;
        private long cpuCount = 2L;
    }

    @Setter
    @Getter
    public static class PlanConfig {
        private boolean enabled = false;
        private String fileDirectory = "plans";
        private boolean allowShell = false;
    }

    /**
     * Harness layered memory configuration (S5).
     * <p>
     * Drives HarnessAgent.Builder.memory(MemoryConfig). When configured, the agent
     * gains three capabilities:
     * <ul>
     *   <li>Per-turn flush — extracts facts to memory/daily/&lt;date&gt;.md</li>
     *   <li>Throttled consolidation — merges daily entries into MEMORY.md</li>
     *   <li>Memory tools — memory_search, memory_get, memory_save</li>
     * </ul>
     * This is the GA replacement for the deprecated v1 LongTermMemory API.
     */
    @Setter
    @Getter
    public static class MemoryConfig {
        /** Model name for flush/consolidation LLM calls; null = use agent's main model */
        private String model;
        /** "ALWAYS" | "NEVER" | "THROTTLED"; null = ALWAYS (framework default) */
        private String flushTrigger;
        /** Min gap between consolidation runs (seconds); only for THROTTLED; null = 1800 (30 min) */
        private Long consolidationMinGapSeconds;
        /** Max tokens for consolidated memory; null = 4000 (framework default) */
        private Integer consolidationMaxTokens;
        /** Daily flush file retention; null = 90 (framework default) */
        private Integer dailyFileRetentionDays;
        /** Session tree retention; null = 180 (framework default) */
        private Integer sessionRetentionDays;
    }

    /**
     * Skill self-learning configuration (S10).
     * <p>
     * Enables the three-stage skill lifecycle: propose → review → promote.
     * When enabled, the agent can create new skills via propose_skill,
     * a promotion gate reviews them, and a curator auto-archives stale skills.
     */
    @Setter
    @Getter
    public static class SkillLearningConfig {
        /** Enable propose_skill / skill_manage tools */
        private boolean manageToolEnabled = false;
        /** Auto-promote approved skills without manual review */
        private boolean autoPromote = false;
        /** Enable security scan on proposed skills */
        private boolean securityScan = true;
        /** Enable curator (auto-archive stale skills) */
        private boolean curatorEnabled = false;
        /** Hours between curator runs; null = framework default */
        private Integer curatorIntervalHours;
        /** Days before a skill is considered stale; null = 30 */
        private Integer staleAfterDays;
        /** Days before a stale skill is archived; null = 90 */
        private Integer archiveAfterDays;
    }
}

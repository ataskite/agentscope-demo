package com.skloda.agentscope.agent;

import com.skloda.agentscope.mcp.McpServerRef;
import com.skloda.agentscope.mcp.ToolGroupConfig;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class AgentConfig {

    private String agentId;
    private String name;
    private String description;
    private String systemPrompt;
    private String modelName = "qwen-plus";
    private boolean streaming = true;
    private boolean enableThinking = true;
    private List<String> skills = new ArrayList<>();
    private List<String> userTools = new ArrayList<>();
    private List<String> systemTools = new ArrayList<>();

    // AutoContextMemory settings
    private boolean autoContext = false;
    private int autoContextMsgThreshold = 30;
    private int autoContextLastKeep = 10;
    private double autoContextTokenRatio = 0.3;

    /** @deprecated v1 RAG API, pending v2 rewrite. Consider Harness MemoryConfig for new agents. */
    @Deprecated(forRemoval = true)
    private boolean ragEnabled = false;
    @Deprecated(forRemoval = true)
    private int ragRetrieveLimit = 3;
    @Deprecated(forRemoval = true)
    private double ragScoreThreshold = 0.5;
    @Deprecated(forRemoval = true)
    private String ragMode = "generic";

    // Modality settings
    private String modality = "text"; // text, vision, audio

    // Agent category for UI grouping
    private String category = "single"; // single, expert, collaboration

    // HITL approval settings
    private boolean approvalRequired = false;
    private List<String> approvalTools = new ArrayList<>();

    // Structured output settings
    private String structuredOutputClass;

    // PlanNotebook settings
    private boolean planEnabled = false;

    /** @deprecated Use {@link HarnessConfig.MemoryConfig} (Harness layered memory) instead. */
    @Deprecated(forRemoval = true)
    private LongTermMemoryConfig longTermMemory;

    // === Multi-agent fields ===
    private AgentType type = AgentType.SINGLE;
    private List<SubAgentConfig> subAgents = new ArrayList<>();
    private Boolean parallel = false;
    private List<HandoffTrigger> handoffTriggers = new ArrayList<>();

    // === P6 Advanced multi-agent fields ===
    private LoopConfig loopConfig;
    private List<StateConfig> states = new ArrayList<>();
    private MsgHubConfig msgHubConfig;

    // === Supervisor / Router + Shared Blackboard fields ===
    // When sharedBlackboard is non-null and enabled, a ROUTING agent is upgraded to a
    // Supervisor that uses an explicit Shared Blackboard (stored under key
    // "shared_blackboard" in the AgentStateStore, never mixed with "agent_state").
    // When null or disabled, the agent keeps the legacy ROUTING/HANDOFFS behavior.
    private SharedBlackboardConfig sharedBlackboard;
    private RoutingConfig routingConfig;

    // === Harness fields ===
    private HarnessConfig harnessConfig;

    // === MCP fields ===
    private List<McpServerRef> mcpServers = new ArrayList<>();
    private List<ToolGroupConfig> toolGroups = new ArrayList<>();

    // === Showcase fields ===
    private List<SamplePrompt> samplePrompts = new ArrayList<>();

    // === Middleware fields ===
    private List<String> middlewares = new ArrayList<>();

    // === Permission fields ===
    private PermissionConfig permissionConfig;

    // === Session fields ===
    private SessionConfig sessionConfig;

    @Setter
    @Getter
    public static class PermissionConfig {
        private String defaultMode = "bypass";
        private List<String> denyTools = new ArrayList<>();
        private List<String> askTools = new ArrayList<>();
    }

    @Setter
    @Getter
    public static class SessionConfig {
        private String defaultType = "memory";
        private String storagePath;
    }

    /**
     * @deprecated Use {@link HarnessConfig.MemoryConfig} instead.
     * The v1 LongTermMemory API (Bailian) is deprecated in GA; the Harness
     * layered memory (MEMORY.md + daily fact log + compaction) is the
     * recommended replacement. See the memory-assistant demo agent.
     */
    @Deprecated(forRemoval = true)
    @Setter
    @Getter
    public static class LongTermMemoryConfig {
        private String type = "none";
        private String mode = "STATIC_CONTROL";
        private String userId = "default_user";
    }

    /**
     * Static configuration for the Supervisor Shared Blackboard.
     * Runtime state (activeExpert, currentIntent, customerFacts, ...) is NEVER stored
     * here — only tunable knobs. The actual runtime data lives in {@code SessionBlackboard}
     * keyed by {@code (userId, sessionId)} under AgentStateStore key "shared_blackboard".
     */
    @Setter
    @Getter
    public static class SharedBlackboardConfig {
        /** Master switch. Default false preserves legacy ROUTING/HANDOFFS behavior. */
        private boolean enabled = false;

        /**
         * AgentStateStore key used to persist the blackboard. MUST be distinct from
         * "agent_state" (the conversation state). Default "shared_blackboard".
         */
        private String storageKey = "shared_blackboard";

        /**
         * Minimum confidence (0..1) below which the Supervisor emits CLARIFY instead of
         * routing. Defaults to 0.5. Only consulted by LLM-based routing strategy.
         */
        private double minConfidence = 0.5;
    }

    /**
     * Static routing-strategy parameters. The runtime router reads these knobs; it does
     * NOT read or write activeExpert / currentIntent from here — those are runtime values
     * kept in {@link SharedBlackboardConfig}'s blackboard.
     */
    @Setter
    @Getter
    public static class RoutingConfig {
        /**
         * Strategy name. Supported: "rule" (default, keyword/intent table from
         * {@link HandoffTrigger}), "llm" (Supervisor LLM decides and emits structured
         * JSON). New strategies can be added without touching YAML schema.
         */
        private String strategy = "rule";

        /**
         * When the router is uncertain (no keyword hit and no LLM quorum), default to
         * KEEP the current activeExpert instead of CLARIFY if the blackboard has one.
         * Set false to always CLARIFY on uncertainty.
         */
        private boolean defaultKeep = true;

        /**
         * Optional model override for the LLM router. If null, the supervisor agent's
         * own {@code modelName} is used.
         */
        private String modelName;

        /** Number of recent Supervisor turns passed to the LLM router as context. */
        private int recentTurns = 3;
    }
}

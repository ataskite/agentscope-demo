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

    // RAG settings
    private boolean ragEnabled = false;
    private int ragRetrieveLimit = 3;
    private double ragScoreThreshold = 0.5;
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
    private String structuredOutputReminder = "TOOL_CHOICE"; // TOOL_CHOICE or PROMPT

    // PlanNotebook settings
    private boolean planEnabled = false;

    // Long-term memory settings
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

    // === Harness fields ===
    private HarnessConfig harnessConfig;

    // === MCP fields ===
    private List<McpServerRef> mcpServers = new ArrayList<>();
    private List<ToolGroupConfig> toolGroups = new ArrayList<>();

    // === Showcase fields ===
    private List<SamplePrompt> samplePrompts = new ArrayList<>();

    @Setter
    @Getter
    public static class LongTermMemoryConfig {
        private String type = "none";
        private String mode = "STATIC_CONTROL";
        private String userId = "default_user";
    }

}

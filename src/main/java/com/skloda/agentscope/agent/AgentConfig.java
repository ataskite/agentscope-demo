package com.skloda.agentscope.agent;

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

    // Modality settings
    private String modality = "text"; // text, vision, audio

    // === Multi-agent fields ===
    private AgentType type = AgentType.SINGLE;
    private List<SubAgentConfig> subAgents = new ArrayList<>();
    private Boolean parallel = false;
    private List<HandoffTrigger> handoffTriggers = new ArrayList<>();

}

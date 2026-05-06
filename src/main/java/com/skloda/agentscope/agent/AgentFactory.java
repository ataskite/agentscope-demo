package com.skloda.agentscope.agent;

import com.skloda.agentscope.service.KnowledgeService;
import com.skloda.agentscope.tool.ToolRegistry;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.memory.bailian.BailianLongTermMemory;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.memory.autocontext.ContextOffloadTool;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.core.rag.RAGMode;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    @Value("${agentscope.model.dashscope.api-key:}")
    private String apiKey;

    private final AgentConfigService configService;
    private final ToolRegistry toolRegistry;
    private final KnowledgeService knowledgeService;

    public AgentFactory(AgentConfigService configService, ToolRegistry toolRegistry,
                        KnowledgeService knowledgeService) {
        this.configService = configService;
        this.toolRegistry = toolRegistry;
        this.knowledgeService = knowledgeService;
    }

    /**
     * Create memory based on agent config (AutoContextMemory or InMemoryMemory).
     */
    public Memory createMemory(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);

        if (config.isAutoContext()) {
            log.info("Creating AutoContextMemory for agent: {}", agentId);
            AutoContextConfig acConfig = AutoContextConfig.builder()
                    .msgThreshold(config.getAutoContextMsgThreshold())
                    .lastKeep(config.getAutoContextLastKeep())
                    .tokenRatio(config.getAutoContextTokenRatio())
                    .build();

            DashScopeChatModel memoryModel = DashScopeChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(config.getModelName())
                    .build();

            return new AutoContextMemory(acConfig, memoryModel);
        }

        return new InMemoryMemory();
    }

    /**
     * Create agent for a persistent session (with externally created memory + hooks).
     */
    public ReActAgent createAgentForSession(String agentId, Memory memory, Hook... hooks) {
        return buildAgent(agentId, memory, hooks);
    }

    /**
     * Create agent with optional hooks (stateless, creates fresh InMemoryMemory each time).
     */
    public ReActAgent createAgent(String agentId, Hook... hooks) {
        return buildAgent(agentId, new InMemoryMemory(), hooks);
    }

    private ReActAgent buildAgent(String agentId, Memory memory, Hook... hooks) {
        AgentConfig config = configService.getAgentConfig(agentId);
        log.info("Creating agent: {} ({})", config.getName(), agentId);

        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.getModelName())
                .stream(config.isStreaming())
                .enableThinking(config.isEnableThinking())
                .formatter(new DashScopeChatFormatter())
                .build();

        ReActAgent.Builder builder = ReActAgent.builder()
                .name(config.getName())
                .sysPrompt(config.getSystemPrompt())
                .model(model)
                .memory(memory);

        // Enable PlanNotebook if configured
        if (config.isPlanEnabled()) {
            builder.enablePlan();
            log.info("  Enabled PlanNotebook for agent: {}", agentId);
        }

        Toolkit toolkit = new Toolkit();

        // Register ContextOffloadTool for AutoContextMemory
        if (memory instanceof AutoContextMemory acm) {
            toolkit.registerTool(new ContextOffloadTool(acm));
            log.info("  Registered ContextOffloadTool for agent: {}", agentId);
        }

        registerToolsAndSkills(builder, toolkit, config, agentId);

        // Configure structured output if specified
        if (config.getStructuredOutputClass() != null && !config.getStructuredOutputClass().isBlank()) {
            StructuredOutputReminder reminder = "PROMPT".equalsIgnoreCase(config.getStructuredOutputReminder())
                    ? StructuredOutputReminder.PROMPT
                    : StructuredOutputReminder.TOOL_CHOICE;
            builder.structuredOutputReminder(reminder);
            log.info("  Configured structured output for agent: {} (class={}, mode={})",
                    agentId, config.getStructuredOutputClass(), reminder);
        }

        // Register RAG knowledge if enabled
        if (config.isRagEnabled()) {
            RAGMode ragMode = parseRagMode(config.getRagMode());
            builder.knowledge(knowledgeService.getKnowledge())
                    .ragMode(ragMode)
                    .retrieveConfig(RetrieveConfig.builder()
                            .limit(config.getRagRetrieveLimit())
                            .scoreThreshold(config.getRagScoreThreshold())
                            .build());
            log.info("  Enabled RAG for agent: {} (mode={}, limit={}, threshold={})",
                    agentId, ragMode, config.getRagRetrieveLimit(), config.getRagScoreThreshold());
        }

        // Configure long-term memory if enabled
        if (config.getLongTermMemory() != null && !"none".equals(config.getLongTermMemory().getType())) {
            LongTermMemory ltm = createLongTermMemory(config.getLongTermMemory());
            if (ltm != null) {
                LongTermMemoryMode mode = parseLtmMode(config.getLongTermMemory().getMode());
                builder.longTermMemory(ltm).longTermMemoryMode(mode);
                log.info("  Enabled long-term memory for agent: {} (type={}, mode={})",
                        agentId, config.getLongTermMemory().getType(), mode);
            }
        }

        // Register hooks if provided
        if (hooks != null && hooks.length > 0) {
            builder.hooks(List.of(hooks));
            log.info("  Registered {} hooks for agent: {}", hooks.length, agentId);
        }

        return builder.build();
    }

    private void registerToolsAndSkills(ReActAgent.Builder builder, Toolkit toolkit,
                                         AgentConfig config, String agentId) {
        // Collect tool names covered by skills to avoid double registration.
        // SkillBox registers tools into inactive groups — if the same tool is also
        // registered directly (ungrouped, active), isActiveTool checks the inactive
        // group first and rejects the call with "Unauthorized tool call".
        Set<String> skillToolNames = new HashSet<>();

        // Register skills FIRST so we know which tools they cover
        SkillBox skillBox = null;
        if (!config.getSkills().isEmpty()) {
            skillBox = new SkillBox(toolkit);
            try (ClasspathSkillRepository repo = new ClasspathSkillRepository("skills")) {
                for (String skillName : config.getSkills()) {
                    if (toolRegistry.hasTool(skillName)) {
                        skillBox.registration()
                                .skill(repo.getSkill(skillName))
                                .tool(toolRegistry.getTool(skillName))
                                .apply();
                        // Track which @Tool function names this skill covers
                        skillToolNames.addAll(toolRegistry.getToolNamesForClass(skillName));
                        log.info("  Registered skill: {} for agent: {}", skillName, agentId);
                    } else {
                        log.error("  Tool for skill not found in registry: {} (agent: {})", skillName, agentId);
                    }
                }
            } catch (Exception e) {
                log.error("  Failed to load skills for agent: {}", agentId, e);
            }
        }

        // Register user tools, skipping those already covered by skills
        List<String> userToolsFiltered = config.getUserTools().stream()
                .filter(name -> !skillToolNames.contains(name))
                .toList();
        for (Object toolInstance : toolRegistry.getDeduplicatedInstances(userToolsFiltered)) {
            toolkit.registerTool(toolInstance);
            log.info("  Registered user tool class: {} for agent: {}", toolInstance.getClass().getSimpleName(), agentId);
        }
        if (!skillToolNames.isEmpty()) {
            log.info("  Skipped user tools already covered by skills: {} (agent: {})", skillToolNames, agentId);
        }

        // Register system tools (deduplicated — one instance per class)
        for (Object toolInstance : toolRegistry.getDeduplicatedInstances(config.getSystemTools())) {
            toolkit.registerTool(toolInstance);
            log.info("  Registered system tool class: {} for agent: {}", toolInstance.getClass().getSimpleName(), agentId);
        }

        if (skillBox != null) {
            builder.toolkit(toolkit).skillBox(skillBox);
        } else {
            builder.toolkit(toolkit);
        }
    }

    private RAGMode parseRagMode(String value) {
        if (value == null || value.isBlank()) {
            return RAGMode.GENERIC;
        }
        return switch (value.trim().toLowerCase()) {
            case "generic" -> RAGMode.GENERIC;
            case "agentic" -> RAGMode.AGENTIC;
            case "none" -> RAGMode.NONE;
            default -> {
                log.warn("Unknown RAG mode '{}', falling back to GENERIC", value);
                yield RAGMode.GENERIC;
            }
        };
    }

    LongTermMemory createLongTermMemory(AgentConfig.LongTermMemoryConfig config) {
        return switch (config.getType().toLowerCase()) {
            case "bailian" -> BailianLongTermMemory.builder()
                    .apiKey(apiKey)
                    .userId(config.getUserId())
                    .build();
            default -> null;
        };
    }

    LongTermMemoryMode parseLtmMode(String value) {
        if (value == null) return LongTermMemoryMode.STATIC_CONTROL;
        return switch (value.trim().toUpperCase()) {
            case "AGENT_CONTROL" -> LongTermMemoryMode.AGENT_CONTROL;
            case "BOTH" -> LongTermMemoryMode.BOTH;
            default -> LongTermMemoryMode.STATIC_CONTROL;
        };
    }
}

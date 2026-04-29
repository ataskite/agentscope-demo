package com.skloda.agentscope.agent;

import com.skloda.agentscope.service.KnowledgeService;
import com.skloda.agentscope.tool.ToolRegistry;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.memory.autocontext.ContextOffloadTool;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.rag.RAGMode;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

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

        Toolkit toolkit = new Toolkit();

        // Register ContextOffloadTool for AutoContextMemory
        if (memory instanceof AutoContextMemory acm) {
            toolkit.registerTool(new ContextOffloadTool(acm));
            log.info("  Registered ContextOffloadTool for agent: {}", agentId);
        }

        registerToolsAndSkills(builder, toolkit, config, agentId);

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

        // Register hooks if provided
        if (hooks != null && hooks.length > 0) {
            builder.hooks(List.of(hooks));
            log.info("  Registered {} hooks for agent: {}", hooks.length, agentId);
        }

        return builder.build();
    }

    private void registerToolsAndSkills(ReActAgent.Builder builder, Toolkit toolkit,
                                         AgentConfig config, String agentId) {
        // Register user tools (deduplicated — one instance per class)
        for (Object toolInstance : toolRegistry.getDeduplicatedInstances(config.getUserTools())) {
            toolkit.registerTool(toolInstance);
            log.info("  Registered user tool class: {} for agent: {}", toolInstance.getClass().getSimpleName(), agentId);
        }

        // Register system tools (deduplicated — one instance per class)
        for (Object toolInstance : toolRegistry.getDeduplicatedInstances(config.getSystemTools())) {
            toolkit.registerTool(toolInstance);
            log.info("  Registered system tool class: {} for agent: {}", toolInstance.getClass().getSimpleName(), agentId);
        }

        // Register skills with their tool bindings (from skills field in config)
        if (!config.getSkills().isEmpty()) {
            SkillBox skillBox = new SkillBox(toolkit);
            try (ClasspathSkillRepository repo = new ClasspathSkillRepository("skills")) {
                for (String skillName : config.getSkills()) {
                    if (toolRegistry.hasTool(skillName)) {
                        skillBox.registration()
                                .skill(repo.getSkill(skillName))
                                .tool(toolRegistry.getTool(skillName))
                                .apply();
                        log.info("  Registered skill: {} for agent: {}", skillName, agentId);
                    } else {
                        log.error("  Tool for skill not found in registry: {} (agent: {})", skillName, agentId);
                    }
                }
            } catch (Exception e) {
                log.error("  Failed to load skills for agent: {}", agentId, e);
            }
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
}

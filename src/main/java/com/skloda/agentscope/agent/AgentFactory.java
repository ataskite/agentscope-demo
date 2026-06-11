package com.skloda.agentscope.agent;

import com.skloda.agentscope.middleware.MiddlewareRegistry;
import com.skloda.agentscope.mcp.McpClientService;
import com.skloda.agentscope.permission.PermissionContextFactory;
import io.agentscope.core.permission.PermissionContextState;
import com.skloda.agentscope.mcp.McpServerRef;
import com.skloda.agentscope.mcp.ToolGroupConfig;
import com.skloda.agentscope.service.KnowledgeService;
import com.skloda.agentscope.tool.ToolRegistry;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.bailian.BailianLongTermMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.core.rag.RAGMode;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.session.InMemorySession;
import io.agentscope.core.session.JsonSession;
import io.agentscope.core.session.Session;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.SimpleSessionKey;
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
    private final McpClientService mcpClientService;
    private final MiddlewareRegistry middlewareRegistry;
    private final PermissionContextFactory permissionContextFactory;

    public AgentFactory(AgentConfigService configService, ToolRegistry toolRegistry,
                        KnowledgeService knowledgeService, McpClientService mcpClientService,
                        MiddlewareRegistry middlewareRegistry,
                        PermissionContextFactory permissionContextFactory) {
        this.configService = configService;
        this.toolRegistry = toolRegistry;
        this.knowledgeService = knowledgeService;
        this.mcpClientService = mcpClientService;
        this.middlewareRegistry = middlewareRegistry;
        this.permissionContextFactory = permissionContextFactory;
    }

    /**
     * Create a session for a persistent session (2.0 replaces Memory with Session).
     */
    public Session createSession() {
        return new InMemorySession();
    }

    public Session createSession(String type, String storagePath) {
        if ("json".equalsIgnoreCase(type)) {
            java.nio.file.Path dir = (storagePath != null && !storagePath.isBlank())
                    ? java.nio.file.Path.of(storagePath)
                    : java.nio.file.Path.of(System.getProperty("user.home"), ".agentscope", "demo-sessions");
            return new JsonSession(dir);
        }
        return new InMemorySession();
    }

    /**
     * Create agent for a persistent session (with shared session + hooks).
     */
    public ReActAgent createAgentForSession(String agentId, Session session, Hook... hooks) {
        return buildAgent(agentId, session, hooks);
    }

    /**
     * Create agent with optional hooks (stateless, creates fresh Session each time).
     */
    public ReActAgent createAgent(String agentId, Hook... hooks) {
        return buildAgent(agentId, new InMemorySession(), hooks);
    }

    public ReActAgent createAgentForSession(String agentId, Session session, String permissionMode, Hook... hooks) {
        return buildAgentWithPermission(agentId, session, permissionMode, hooks);
    }

    public ReActAgent createAgent(String agentId, String permissionMode, Hook... hooks) {
        return buildAgentWithPermission(agentId, new InMemorySession(), permissionMode, hooks);
    }

    private ReActAgent buildAgent(String agentId, Session session, Hook... hooks) {
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
                .session(session)
                .sessionKey(SimpleSessionKey.of(agentId));

        // Enable PlanNotebook if configured
        if (config.isPlanEnabled()) {
            builder.enablePlan();
            log.info("  Enabled PlanNotebook for agent: {}", agentId);
        }

        Toolkit toolkit = new Toolkit();

        registerToolsAndSkills(builder, toolkit, config, agentId);

        // Register MCP tools after local tools/skills are set up
        registerMcpTools(config, toolkit);

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

        // Register middlewares if configured
        if (config.getMiddlewares() != null && !config.getMiddlewares().isEmpty()) {
            List<MiddlewareBase> middlewares = config.getMiddlewares().stream()
                    .map(middlewareRegistry::create)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!middlewares.isEmpty()) {
                builder.middlewares(middlewares);
                log.info("  Registered {} middlewares for agent: {}", middlewares.size(), agentId);
            }
        }

        builder.enablePendingToolRecovery(true);

        return builder.build();
    }

    private ReActAgent buildAgentWithPermission(String agentId, Session session, String permissionMode, Hook... hooks) {
        AgentConfig config = configService.getAgentConfig(agentId);
        log.info("Creating agent: {} ({}) [permissionMode={}]", config.getName(), agentId, permissionMode);

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
                .session(session)
                .sessionKey(SimpleSessionKey.of(agentId));

        if (config.isPlanEnabled()) {
            builder.enablePlan();
            log.info("  Enabled PlanNotebook for agent: {}", agentId);
        }

        Toolkit toolkit = new Toolkit();
        registerToolsAndSkills(builder, toolkit, config, agentId);
        registerMcpTools(config, toolkit);

        if (config.getStructuredOutputClass() != null && !config.getStructuredOutputClass().isBlank()) {
            StructuredOutputReminder reminder = "PROMPT".equalsIgnoreCase(config.getStructuredOutputReminder())
                    ? StructuredOutputReminder.PROMPT
                    : StructuredOutputReminder.TOOL_CHOICE;
            builder.structuredOutputReminder(reminder);
            log.info("  Configured structured output for agent: {} (class={}, mode={})",
                    agentId, config.getStructuredOutputClass(), reminder);
        }

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

        if (config.getLongTermMemory() != null && !"none".equals(config.getLongTermMemory().getType())) {
            LongTermMemory ltm = createLongTermMemory(config.getLongTermMemory());
            if (ltm != null) {
                LongTermMemoryMode mode = parseLtmMode(config.getLongTermMemory().getMode());
                builder.longTermMemory(ltm).longTermMemoryMode(mode);
                log.info("  Enabled long-term memory for agent: {} (type={}, mode={})",
                        agentId, config.getLongTermMemory().getType(), mode);
            }
        }

        if (hooks != null && hooks.length > 0) {
            builder.hooks(List.of(hooks));
            log.info("  Registered {} hooks for agent: {}", hooks.length, agentId);
        }

        if (config.getMiddlewares() != null && !config.getMiddlewares().isEmpty()) {
            List<MiddlewareBase> middlewares = config.getMiddlewares().stream()
                    .map(middlewareRegistry::create)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!middlewares.isEmpty()) {
                builder.middlewares(middlewares);
                log.info("  Registered {} middlewares for agent: {}", middlewares.size(), agentId);
            }
        }

        // Configure permission context
        String effectiveMode = resolvePermissionMode(config, permissionMode);
        if (effectiveMode != null) {
            PermissionContextState permContext = permissionContextFactory.build(
                    effectiveMode, config.getPermissionConfig());
            builder.permissionContext(permContext);
            log.info("  Configured permission mode: {} for agent: {}", effectiveMode, agentId);
        }

        builder.enablePendingToolRecovery(true);
        return builder.build();
    }

    private String resolvePermissionMode(AgentConfig config, String requestMode) {
        if (config.getPermissionConfig() == null) return null;
        if (requestMode != null && !requestMode.isBlank()) return requestMode;
        return config.getPermissionConfig().getDefaultMode();
    }

    private void registerToolsAndSkills(ReActAgent.Builder builder, Toolkit toolkit,
                                         AgentConfig config, String agentId) {
        Set<String> skillToolNames = new HashSet<>();

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

    private void registerMcpTools(AgentConfig config, Toolkit toolkit) {
        if (config.getMcpServers() == null || config.getMcpServers().isEmpty()) {
            return;
        }

        log.info("Registering MCP tools for agent: {}", config.getAgentId());

        if (config.getToolGroups() != null && !config.getToolGroups().isEmpty()) {
            for (ToolGroupConfig groupConfig : config.getToolGroups()) {
                toolkit.createToolGroup(
                    groupConfig.getName(),
                    groupConfig.getDescription(),
                    groupConfig.getActive() != null ? groupConfig.getActive() : true
                );
                log.debug("Created tool group: {} (active={})", groupConfig.getName(), groupConfig.getActive());
            }
        }

        for (McpServerRef ref : config.getMcpServers()) {
            var clientOpt = mcpClientService.getClient(ref.getServer());
            if (clientOpt.isEmpty()) {
                log.warn("MCP client not found: {}, skipping", ref.getServer());
                continue;
            }

            var client = clientOpt.get();
            var registration = toolkit.registration().mcpClient(client);

            if (ref.getEnableTools() != null && !ref.getEnableTools().isEmpty()) {
                registration.enableTools(ref.getEnableTools());
                log.debug("Enabled tools for {}: {}", ref.getServer(), ref.getEnableTools());
            }
            if (ref.getDisableTools() != null && !ref.getDisableTools().isEmpty()) {
                registration.disableTools(ref.getDisableTools());
                log.debug("Disabled tools for {}: {}", ref.getServer(), ref.getDisableTools());
            }

            if (ref.getGroup() != null) {
                registration.group(ref.getGroup());
                log.debug("Assigned {} to group: {}", ref.getServer(), ref.getGroup());
            }

            registration.apply();
            log.info("Registered MCP tools from: {}", ref.getServer());
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

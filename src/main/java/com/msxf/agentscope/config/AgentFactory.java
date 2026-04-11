package com.msxf.agentscope.config;

import com.msxf.agentscope.tool.ToolRegistry;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    @Value("${agentscope.model.dashscope.api-key:}")
    private String apiKey;

    private final AgentConfigService configService;
    private final ToolRegistry toolRegistry;

    public AgentFactory(AgentConfigService configService, ToolRegistry toolRegistry) {
        this.configService = configService;
        this.toolRegistry = toolRegistry;
    }

    public ReActAgent createAgent(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        log.info("Creating agent: {} ({})", config.getName(), agentId);

        // Build model from config
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.getModelName())
                .stream(config.isStreaming())
                .enableThinking(config.isEnableThinking())
                .formatter(new DashScopeChatFormatter())
                .build();

        // Build agent
        ReActAgent.Builder builder = ReActAgent.builder()
                .name(config.getName())
                .sysPrompt(config.getSystemPrompt())
                .model(model)
                .memory(new InMemoryMemory());

        Toolkit toolkit = new Toolkit();

        // Register tools directly (from tools field in config)
        for (String toolName : config.getTools()) {
            if (toolRegistry.hasTool(toolName)) {
                toolkit.registerTool(toolRegistry.getTool(toolName));
                log.info("  Registered tool: {} for agent: {}", toolName, agentId);
            } else {
                log.error("  Tool not found in registry: {} (agent: {})", toolName, agentId);
            }
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

        return builder.build();
    }
}

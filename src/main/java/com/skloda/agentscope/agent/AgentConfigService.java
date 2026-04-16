package com.skloda.agentscope.agent;

import com.skloda.agentscope.tool.ToolRegistry;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;

@Service
public class AgentConfigService {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigService.class);

    private final Map<String, AgentConfig> configMap = new HashMap<>();
    private final List<AgentConfig> allAgents = new ArrayList<>();
    private final Map<String, String> skillDescriptions = new HashMap<>();

    @Value("classpath:config/agents.yml")
    private Resource configFile;

    @Autowired
    private ToolRegistry toolRegistry;

    @PostConstruct
    public void init() {
        try (InputStream is = configFile.getInputStream()) {
            Yaml yaml = new Yaml(new Constructor(AgentsWrapper.class, new LoaderOptions()));
            AgentsWrapper wrapper = yaml.load(is);

            for (AgentConfig config : wrapper.getAgents()) {
                if (config.getAgentId() == null || config.getAgentId().isBlank()) {
                    log.warn("Skipping agent config with missing agentId");
                    continue;
                }
                if (configMap.containsKey(config.getAgentId())) {
                    log.warn("Duplicate agentId: {}, using first occurrence", config.getAgentId());
                    continue;
                }
                configMap.put(config.getAgentId(), config);
                allAgents.add(config);
                log.info("Loaded agent config: {} ({})", config.getName(), config.getAgentId());
            }

            log.info("Loaded {} agent configurations", allAgents.size());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load agents.yml configuration", e);
        }

        // Load skill descriptions
        loadSkillDescriptions();
    }

    private void loadSkillDescriptions() {
        try {
            for (var entry : toolRegistry.getSkillMetadataMap().entrySet()) {
                ToolRegistry.SkillMetadata meta = entry.getValue();
                skillDescriptions.put(entry.getKey(), meta.description());
            }
            log.info("Loaded {} skill descriptions from registry", skillDescriptions.size());
        } catch (Exception e) {
            log.warn("Failed to load skill descriptions", e);
        }
    }

    /**
     * Get config by agentId.
     * @throws IllegalArgumentException if agentId not found
     */
    public AgentConfig getAgentConfig(String agentId) {
        AgentConfig config = configMap.get(agentId);
        if (config == null) {
            throw new IllegalArgumentException("Agent config not found: " + agentId);
        }
        return config;
    }

    public Optional<AgentConfig> findAgentConfig(String agentId) {
        return Optional.ofNullable(configMap.get(agentId));
    }

    public List<AgentConfig> getAllAgents() {
        return Collections.unmodifiableList(allAgents);
    }

    public Map<String, Object> getSkillInfo(String skillName) {
        log.info("[getSkillInfo] Requested skillName: '{}', available descriptions: {}",
                skillName, skillDescriptions.keySet());

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", skillName);
        info.put("type", "skill");
        String description = skillDescriptions.getOrDefault(skillName, "No description available");
        info.put("description", description);

        log.info("[getSkillInfo] Found description: '{}'", description);

        // Get tools associated with this skill
        List<String> toolNames = toolRegistry.getToolNamesForClass(skillName);
        log.info("[getSkillInfo] Tool names for '{}': {}", skillName, toolNames);
        List<Map<String, String>> toolsInfo = new ArrayList<>();
        for (String toolName : toolNames) {
            Map<String, String> toolDetail = new HashMap<>();
            toolDetail.put("name", toolName);
            toolDetail.put("description", getToolDescription(toolName));
            toolsInfo.add(toolDetail);
        }
        info.put("tools", toolsInfo);

        return info;
    }

    public Map<String, Object> getToolInfo(String toolName) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", toolName);
        info.put("type", "tool");
        info.put("description", getToolDescription(toolName));

        // Get parameters info
        String className = toolRegistry.getClassName(toolName);
        if (className != null) {
            try {
                Object toolInstance = toolRegistry.getTool(toolName);
                Method[] methods = toolInstance.getClass().getMethods();
                for (Method method : methods) {
                    Tool toolAnnotation = method.getAnnotation(Tool.class);
                    if (toolAnnotation != null && toolName.equals(toolAnnotation.name())) {
                        List<Map<String, String>> params = new ArrayList<>();
                        for (java.lang.reflect.Parameter param : method.getParameters()) {
                            ToolParam paramAnnotation = param.getAnnotation(ToolParam.class);
                            if (paramAnnotation != null) {
                                Map<String, String> paramInfo = new HashMap<>();
                                paramInfo.put("name", paramAnnotation.name());
                                paramInfo.put("description", paramAnnotation.description());
                                paramInfo.put("type", param.getType().getSimpleName());
                                params.add(paramInfo);
                            }
                        }
                        info.put("parameters", params);
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get tool info for {}", toolName, e);
            }
        }

        return info;
    }

    private String getToolDescription(String toolName) {
        try {
            Object toolInstance = toolRegistry.getTool(toolName);
            Method[] methods = toolInstance.getClass().getMethods();
            for (Method method : methods) {
                Tool toolAnnotation = method.getAnnotation(Tool.class);
                if (toolAnnotation != null && toolName.equals(toolAnnotation.name())) {
                    return toolAnnotation.description();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get tool description for {}", toolName, e);
        }
        return "No description available";
    }

    @Setter
    @Getter
    public static class AgentsWrapper {
        private List<AgentConfig> agents = new ArrayList<>();

    }

}

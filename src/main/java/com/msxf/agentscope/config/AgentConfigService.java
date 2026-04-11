package com.msxf.agentscope.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

@Service
public class AgentConfigService {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigService.class);

    private final Map<String, AgentConfig> configMap = new LinkedHashMap<>();
    private final List<AgentConfig> allAgents = new ArrayList<>();

    @Value("classpath:config/agents.json")
    private Resource configFile;

    @PostConstruct
    public void init() {
        try (InputStream is = configFile.getInputStream()) {
            ObjectMapper mapper = new ObjectMapper();
            AgentsConfig agentsConfig = mapper.readValue(is, AgentsConfig.class);

            for (AgentConfig config : agentsConfig.getAgents()) {
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
            throw new IllegalStateException("Failed to load agents.json configuration", e);
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

    public boolean exists(String agentId) {
        return configMap.containsKey(agentId);
    }
}

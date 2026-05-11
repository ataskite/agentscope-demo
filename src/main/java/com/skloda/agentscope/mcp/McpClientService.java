package com.skloda.agentscope.mcp;

import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that loads MCP server configurations from mcp-servers.yml and manages
 * McpClientWrapper instances for each configured server.
 * <p>
 * Supports STDIO, SSE, and HTTP (StreamableHTTP) transports with environment
 * variable placeholder resolution in header and queryParam values.
 */
@Service
@DependsOn("mcpDemoServer")
public class McpClientService {

    private static final Logger log = LoggerFactory.getLogger(McpClientService.class);

    @Value("classpath:config/mcp-servers.yml")
    private Resource configFile;

    @Value("${agentscope.mcp.enabled:true}")
    private boolean mcpEnabled;

    private final Map<String, McpClientWrapper> clients = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (!mcpEnabled) {
            log.info("MCP client service is disabled (agentscope.mcp.enabled=false)");
            return;
        }

        McpServersWrapper wrapper;
        try (InputStream is = configFile.getInputStream()) {
            Yaml yaml = new Yaml(new Constructor(McpServersWrapper.class, new LoaderOptions()));
            wrapper = yaml.load(is);
        } catch (Exception e) {
            log.warn("Failed to load mcp-servers.yml — no MCP clients will be available", e);
            return;
        }

        if (wrapper.getServers() == null || wrapper.getServers().isEmpty()) {
            log.info("No MCP servers configured in mcp-servers.yml");
            return;
        }

        for (McpServerConfig config : wrapper.getServers()) {
            try {
                McpClientWrapper client = buildClient(config);
                if (client != null) {
                    clients.put(config.getName(), client);
                    log.info("Initialized MCP client: {} (transport={})", config.getName(), config.getTransport());
                }
            } catch (Exception e) {
                log.error("Failed to initialize MCP client '{}': {}", config.getName(), e.getMessage(), e);
            }
        }

        log.info("MCP client service initialized with {} active client(s)", clients.size());
    }

    @PreDestroy
    public void destroy() {
        for (Map.Entry<String, McpClientWrapper> entry : clients.entrySet()) {
            try {
                entry.getValue().close();
                log.info("Closed MCP client: {}", entry.getKey());
            } catch (Exception e) {
                log.warn("Error closing MCP client '{}': {}", entry.getKey(), e.getMessage());
            }
        }
        clients.clear();
    }

    /**
     * Get an MCP client by server name.
     *
     * @param serverName the configured server name
     * @return Optional containing the client wrapper, or empty if not found
     */
    public Optional<McpClientWrapper> getClient(String serverName) {
        return Optional.ofNullable(clients.get(serverName));
    }

    /**
     * Get all initialized MCP clients.
     *
     * @return unmodifiable map of server name to client wrapper
     */
    public Map<String, McpClientWrapper> getAllClients() {
        return Collections.unmodifiableMap(clients);
    }

    /**
     * Check if MCP is enabled and has active clients.
     */
    public boolean isActive() {
        return mcpEnabled && !clients.isEmpty();
    }

    private McpClientWrapper buildClient(McpServerConfig config) {
        if (config.getName() == null || config.getName().isBlank()) {
            log.warn("Skipping MCP server config with missing name");
            return null;
        }

        McpClientBuilder builder = McpClientBuilder.create(config.getName());

        switch (config.getTransport()) {
            case STDIO -> configureStdio(builder, config);
            case SSE -> configureSse(builder, config);
            case HTTP -> configureHttp(builder, config);
            default -> {
                log.warn("Unknown transport type '{}' for server '{}'", config.getTransport(), config.getName());
                return null;
            }
        }

        if (config.getTimeout() != null) {
            builder.timeout(Duration.ofSeconds(config.getTimeout()));
        }
        if (config.getInitTimeout() != null) {
            builder.initializationTimeout(Duration.ofSeconds(config.getInitTimeout()));
        }

        return builder.buildAsync().block();
    }

    private void configureStdio(McpClientBuilder builder, McpServerConfig config) {
        if (config.getCommand() == null || config.getCommand().isBlank()) {
            throw new IllegalArgumentException("STDIO transport requires 'command' for server: " + config.getName());
        }
        List<String> args = config.getArgs() != null ? config.getArgs() : List.of();
        builder.stdioTransport(config.getCommand(), args.toArray(new String[0]));
    }

    private void configureSse(McpClientBuilder builder, McpServerConfig config) {
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            throw new IllegalArgumentException("SSE transport requires 'url' for server: " + config.getName());
        }
        builder.sseTransport(config.getUrl());
        applyHttpConfig(builder, config);
    }

    private void configureHttp(McpClientBuilder builder, McpServerConfig config) {
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            throw new IllegalArgumentException("HTTP transport requires 'url' for server: " + config.getName());
        }
        builder.streamableHttpTransport(config.getUrl());
        applyHttpConfig(builder, config);
    }

    private void applyHttpConfig(McpClientBuilder builder, McpServerConfig config) {
        if (config.getHeaders() != null) {
            for (Map.Entry<String, String> entry : config.getHeaders().entrySet()) {
                builder.header(entry.getKey(), resolvePlaceholders(entry.getValue()));
            }
        }
        if (config.getQueryParams() != null) {
            for (Map.Entry<String, String> entry : config.getQueryParams().entrySet()) {
                builder.queryParam(entry.getKey(), resolvePlaceholders(entry.getValue()));
            }
        }
    }

    /**
     * Resolves ${ENV_VAR:default} placeholders in string values.
     * Format: ${VAR_NAME} or ${VAR_NAME:defaultValue}
     */
    private String resolvePlaceholders(String value) {
        if (value == null) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            int start = value.indexOf("${", i);
            if (start == -1) {
                result.append(value, i, value.length());
                break;
            }
            result.append(value, i, start);
            int end = value.indexOf('}', start);
            if (end == -1) {
                result.append(value, start, value.length());
                break;
            }
            String expr = value.substring(start + 2, end);
            result.append(resolveEnvExpression(expr));
            i = end + 1;
        }
        return result.toString();
    }

    private String resolveEnvExpression(String expr) {
        int colonIndex = expr.indexOf(':');
        if (colonIndex == -1) {
            // ${VAR} — no default
            String envValue = System.getenv(expr);
            return envValue != null ? envValue : "";
        }
        // ${VAR:default}
        String varName = expr.substring(0, colonIndex);
        String defaultValue = expr.substring(colonIndex + 1);
        String envValue = System.getenv(varName);
        return envValue != null ? envValue : defaultValue;
    }
}

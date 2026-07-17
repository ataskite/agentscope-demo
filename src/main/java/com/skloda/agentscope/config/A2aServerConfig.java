package com.skloda.agentscope.config;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.model.ModelFactory;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.InMemoryAgentStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * S12: A2A Protocol server-side configuration.
 * <p>
 * Activated via {@code --spring.profiles.active=a2a}. Exposes a {@link ReActAgent.Builder}
 * bean that the {@code agentscope-a2a-spring-boot-starter} auto-config picks up to build an
 * A2A server (serving {@code /.well-known/agent-card.json} and JSON-RPC task endpoints).
 * <p>
 * The exposed agent is the {@code chat-basic} config (a simple chat agent) - chosen as a
 * stable, dependency-free showcase. The A2A client demo ({@code A2aClientDemoAgent}) calls
 * this server in a self-loop (same application), demonstrating one agent registering a
 * capability and another invoking it via the A2A protocol.
 *
 * <h3>Prerequisites</h3>
 * {@code agentscope.a2a.common.enabled=true} must be set (see {@code application-a2a.yml})
 * for the auto-config to activate the A2A controllers.
 *
 * <h3>Endpoints (when active)</h3>
 * <ul>
 *   <li>{@code GET /.well-known/agent-card.json} - A2A AgentCard discovery</li>
 *   <li>{@code POST /a2a/jsonrpc} - JSON-RPC task send/get/cancel</li>
 * </ul>
 */
@Configuration
@Profile("a2a")
public class A2aServerConfig {

    private static final Logger log = LoggerFactory.getLogger(A2aServerConfig.class);

    /**
     * Expose a ReActAgent.Builder bean for the A2A auto-config.
     * The auto-config's {@code agentRunnerWithBuilder} wraps it into an
     * {@code AgentRunner} -> {@code AgentScopeA2aServer}.
     * <p>
     * Uses the {@code chat-basic} agent config as the exposed A2A agent.
     */
    @Bean
    public ReActAgent.Builder a2aExposedAgentBuilder(AgentConfigService configService, ModelFactory modelFactory) {
        AgentConfig config = configService.getAgentConfig("chat-basic");
        String modelName = config.getModelName() != null ? config.getModelName() : "qwen-plus";
        Model model = modelFactory.createModel(modelName, config.isStreaming(), config.isEnableThinking());
        log.info("S12: A2A server agent builder created (exposing agent 'chat-basic', model={})", modelName);
        return ReActAgent.builder()
                .name(config.getName() != null ? config.getName() : "a2a-chat-agent")
                .sysPrompt(config.getSystemPrompt())
                .model(model)
                .stateStore(new InMemoryAgentStateStore())
                .defaultSessionId("a2a-default");
    }
}

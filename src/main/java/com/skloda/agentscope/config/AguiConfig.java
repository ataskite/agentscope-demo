package com.skloda.agentscope.config;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.model.ModelFactory;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.spring.boot.agui.common.AguiAgentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * S15: AG-UI Protocol configuration.
 * <p>
 * Activated via {@code --spring.profiles.active=agui}. Registers the {@code chat-basic}
 * agent into the {@code AguiAgentRegistry} (auto-configured by
 * {@code agentscope-agui-spring-boot-starter}) so it is callable via the AG-UI protocol
 * at {@code POST /ag-ui} (path configurable via {@code agentscope.agui.path-prefix}).
 * <p>
 * The existing {@code /chat/send} SSE endpoint (custom {@code AgentEventMapper} format)
 * is left untouched. AG-UI provides a standardized event protocol (28 AguiEventType values)
 * consumed by AG-UI-compatible frontends (e.g. CopilotKit).
 *
 * <h3>Endpoints (when active)</h3>
 * <ul>
 *   <li>{@code POST /ag-ui} - AG-UI SSE endpoint (RunAgentInput -> Flux&lt;AguiEvent&gt;)</li>
 * </ul>
 *
 * <h3>Frontend</h3>
 * A minimal {@code agui.html} page is provided to exercise the endpoint without a full
 * AG-UI-compatible frontend.
 */
@Configuration
@Profile("agui")
public class AguiConfig {

    private static final Logger log = LoggerFactory.getLogger(AguiConfig.class);

    /**
     * Register the chat-basic agent as an AG-UI-callable Agent bean.
     * The {@code @AguiAgentId} annotation tells {@code AguiAgentAutoRegistration}
     * to register it in the {@code AguiAgentRegistry} under the given id.
     */
    @Bean
    @AguiAgentId("chat-basic")
    public Agent aguiChatAgent(AgentConfigService configService, ModelFactory modelFactory) {
        AgentConfig config = configService.getAgentConfig("chat-basic");
        String modelName = config.getModelName() != null ? config.getModelName() : "qwen-plus";
        Model model = modelFactory.createModel(modelName, config.isStreaming(), config.isEnableThinking());
        log.info("S15: AG-UI agent registered (id=chat-basic, model={})", modelName);
        return ReActAgent.builder()
                .name(config.getName() != null ? config.getName() : "agui-chat-agent")
                .sysPrompt(config.getSystemPrompt())
                .model(model)
                .stateStore(new InMemoryAgentStateStore())
                .defaultSessionId("agui-default")
                .build();
    }
}

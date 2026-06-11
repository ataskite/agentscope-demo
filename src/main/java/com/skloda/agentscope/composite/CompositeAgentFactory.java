package com.skloda.agentscope.composite;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentFactory;
import com.skloda.agentscope.agent.AgentType;
import com.skloda.agentscope.agent.HandoffTrigger;
import com.skloda.agentscope.agent.StateConfig;
import com.skloda.agentscope.agent.SubAgentConfig;
import com.skloda.agentscope.agent.TriggerType;
import com.skloda.agentscope.hook.ApprovalHook;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.session.Session;
import io.agentscope.core.session.InMemorySession;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.SimpleSessionKey;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.subagent.SubAgentProvider;
import io.agentscope.core.tool.subagent.SubAgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.skloda.agentscope.composite.graph.OrderFulfillmentGraph;

/**
 * Factory for creating multi-agent compositions.
 * SINGLE agents delegate to AgentFactory.
 * ROUTING/HANDOFFS agents use SubAgentTool for dynamic dispatch.
 * STATE_GRAPH agents use custom OrderFulfillmentGraph.
 *
 * Pipeline-dependent patterns (SEQUENTIAL, PARALLEL, DEBATE, LOOP, MSG_HUB,
 * SUBAGENT_SEQ, SUBAGENT_PAR) are disabled during AgentScope 2.0 migration
 * (io.agentscope.core.pipeline.* removed). They will be reimplemented using
 * 2.0 subagent/middleware API.
 */
@Component
public class CompositeAgentFactory {
    private static final Logger log = LoggerFactory.getLogger(CompositeAgentFactory.class);

    private final AgentFactory singleAgentFactory;
    private final AgentConfigService configService;

    @Value("${agentscope.model.dashscope.api-key:}")
    private String apiKey;

    public CompositeAgentFactory(AgentFactory singleAgentFactory, AgentConfigService configService) {
        this.singleAgentFactory = singleAgentFactory;
        this.configService = configService;
    }

    public ReActAgent createSingleAgent(String agentId, Hook... hooks) {
        return singleAgentFactory.createAgent(agentId, hooks);
    }

    public ReActAgent createSingleAgent(String agentId, Hook hook, ApprovalHook approvalHook) {
        return singleAgentFactory.createAgent(agentId, mergeHooks(hook, approvalHook));
    }

    public ReActAgent createSingleAgent(String agentId, String permissionMode, Hook... hooks) {
        return singleAgentFactory.createAgent(agentId, permissionMode, hooks);
    }

    public ReActAgent createSingleAgent(String agentId, String permissionMode, Hook hook, ApprovalHook approvalHook) {
        return singleAgentFactory.createAgent(agentId, permissionMode, mergeHooks(hook, approvalHook));
    }

    /**
     * Create a single agent for session use (with externally provided Session).
     */
    public ReActAgent createSingleAgentForSession(String agentId, Session session, Hook... hooks) {
        return singleAgentFactory.createAgentForSession(agentId, session, hooks);
    }

    public ReActAgent createSingleAgentForSession(String agentId, Session session, Hook hook, ApprovalHook approvalHook) {
        return singleAgentFactory.createAgentForSession(agentId, session, mergeHooks(hook, approvalHook));
    }

    public ReActAgent createSingleAgentForSession(String agentId, Session session, String permissionMode, Hook... hooks) {
        return singleAgentFactory.createAgentForSession(agentId, session, permissionMode, hooks);
    }

    public ReActAgent createSingleAgentForSession(String agentId, Session session, String permissionMode, Hook hook, ApprovalHook approvalHook) {
        return singleAgentFactory.createAgentForSession(agentId, session, permissionMode, mergeHooks(hook, approvalHook));
    }

    /**
     * Merge hooks into a single array for vararg methods.
     */
    private Hook[] mergeHooks(Hook hook, ApprovalHook approvalHook) {
        if (approvalHook == null) {
            return new Hook[] { hook };
        }
        return new Hook[] { hook, approvalHook };
    }

    /**
     * @deprecated Use {@link #createSingleAgentForSession(String, Session, Hook...)} instead.
     */
    @Deprecated
    public ReActAgent createSingleAgentForSession(String agentId, io.agentscope.core.memory.Memory memory, Hook... hooks) {
        return singleAgentFactory.createAgent(agentId, hooks);
    }

    /**
     * @deprecated Use {@link #createSingleAgentForSession(String, Session, Hook...)} instead.
     */
    @Deprecated
    public ReActAgent createSingleAgentForSession(String agentId, io.agentscope.core.memory.Memory memory, Hook hook, ApprovalHook approvalHook) {
        return singleAgentFactory.createAgent(agentId, mergeHooks(hook, approvalHook));
    }

    public Session createSession() {
        return singleAgentFactory.createSession();
    }

    public List<AgentBase> createSubAgents(AgentConfig config) {
        return config.getSubAgents().stream()
                .map(sub -> {
                    log.info("Creating sub-agent: {} for composite: {}", sub.getAgentId(), config.getAgentId());
                    return singleAgentFactory.createAgent(sub.getAgentId());
                })
                .map(ReActAgent.class::cast)
                .map(AgentBase.class::cast)
                .toList();
    }

    public OrderFulfillmentGraph createStateGraphAgent(AgentConfig config, Session session) {
        List<StateConfig> states = config.getStates();
        if (states == null || states.isEmpty()) {
            throw new IllegalArgumentException("STATE_GRAPH requires states configuration");
        }

        Map<String, ReActAgent> stateAgents = new LinkedHashMap<>();
        for (StateConfig state : states) {
            if (state.getAgent() != null) {
                Session effectiveSession = session != null ? session : new InMemorySession();
                ReActAgent agent = singleAgentFactory.createAgentForSession(state.getAgent(), effectiveSession);
                stateAgents.put(state.getName(), agent);
            }
        }

        return new OrderFulfillmentGraph(states, stateAgents);
    }

    /**
     * @deprecated Use {@link #createStateGraphAgent(AgentConfig, Session)} instead.
     */
    @Deprecated
    public OrderFulfillmentGraph createStateGraphAgent(AgentConfig config, io.agentscope.core.memory.Memory memory) {
        return createStateGraphAgent(config, (Session) null);
    }

    public ReActAgent createRoutingAgent(AgentConfig config, Session session, Hook... hooks) {
        if (config.getSubAgents() == null || config.getSubAgents().isEmpty()) {
            throw new IllegalArgumentException("ROUTING agent requires at least one sub-agent: " + config.getAgentId());
        }

        log.info("Creating ROUTING agent for: {} with {} sub-agents", config.getAgentId(), config.getSubAgents().size());

        Session effectiveSession = session != null ? session : new InMemorySession();

        String routingPrompt = buildRoutingSystemPrompt(config);

        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.getModelName())
                .stream(config.isStreaming())
                .enableThinking(config.isEnableThinking())
                .formatter(new DashScopeChatFormatter())
                .build();

        Toolkit toolkit = new Toolkit();
        List<ReActAgent> subAgents = new ArrayList<>();

        for (SubAgentConfig subConfig : config.getSubAgents()) {
            AgentConfig subAgentConfig = configService.getAgentConfig(subConfig.getAgentId());

            String originalPrompt = subAgentConfig != null ? subAgentConfig.getSystemPrompt() : "";
            String modifiedPrompt = "你是一个子代理，正在通过工具调用被主代理调用。\n" +
                    "请直接回答用户的问题，不要调用任何工具。\n" +
                    "专注于你作为" + subConfig.getAgentId() + "的专业领域。\n\n" +
                    "你的原始角色描述:\n" + originalPrompt;

            DashScopeChatModel subModel = DashScopeChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(subAgentConfig != null ? subAgentConfig.getModelName() : config.getModelName())
                    .stream(config.isStreaming())
                    .enableThinking(config.isEnableThinking())
                    .formatter(new DashScopeChatFormatter())
                    .build();

            ReActAgent subAgent = ReActAgent.builder()
                    .name(subConfig.getAgentId())
                    .sysPrompt(modifiedPrompt)
                    .model(subModel)
                    .session(new InMemorySession())
                    .sessionKey(SimpleSessionKey.of(subConfig.getAgentId()))
                    .toolkit(new Toolkit())
                    .enablePendingToolRecovery(true)
                    .build();

            subAgents.add(subAgent);

            SubAgentProvider<ReActAgent> provider = () -> subAgent;

            io.agentscope.core.tool.subagent.SubAgentConfig frameworkSubConfig =
                    io.agentscope.core.tool.subagent.SubAgentConfig.builder()
                            .toolName(subConfig.getAgentId())
                            .description(subConfig.getDescription() != null
                                    ? subConfig.getDescription()
                                    : "Sub-agent: " + subConfig.getAgentId())
                            .forwardEvents(true)
                            .streamOptions(StreamOptions.builder()
                                    .eventTypes(io.agentscope.core.agent.EventType.REASONING,
                                            io.agentscope.core.agent.EventType.TOOL_RESULT)
                                    .incremental(true)
                                    .includeReasoningResult(true)
                                    .build())
                            .build();

            SubAgentTool subAgentTool = new SubAgentTool(provider, frameworkSubConfig);
            toolkit.registerTool(subAgentTool);
            log.info("  Registered SubAgentTool: {} for routing agent: {} (no-tool mode)",
                    subConfig.getAgentId(), config.getAgentId());
        }

        ReActAgent.Builder builder = ReActAgent.builder()
                .name(config.getName() != null ? config.getName() : config.getAgentId())
                .sysPrompt(routingPrompt)
                .model(model)
                .session(effectiveSession)
                .sessionKey(SimpleSessionKey.of(config.getAgentId()))
                .toolkit(toolkit);

        if (hooks != null && hooks.length > 0) {
            builder.hooks(List.of(hooks));
        }

        return builder.build();
    }

    /**
     * @deprecated Use {@link #createRoutingAgent(AgentConfig, Session, Hook...)} instead.
     */
    @Deprecated
    public ReActAgent createRoutingAgent(AgentConfig config, io.agentscope.core.memory.Memory memory, Hook... hooks) {
        return createRoutingAgent(config, (Session) null, hooks);
    }

    private String buildRoutingSystemPrompt(AgentConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个智能路由助手，负责将用户请求分配给最合适的子代理处理。\n\n");
        sb.append("## 你的职责\n");
        sb.append("分析用户的请求，选择最合适的子代理来处理。你应该：\n");
        sb.append("1. 理解用户的意图\n");
        sb.append("2. 选择最匹配的子代理\n");
        sb.append("3. 调用对应的子代理工具\n\n");

        if (config.getDescription() != null && !config.getDescription().isBlank()) {
            sb.append("## 路由器描述\n");
            sb.append(config.getDescription()).append("\n\n");
        }

        sb.append("## 可用的子代理\n");
        for (SubAgentConfig subConfig : config.getSubAgents()) {
            sb.append("- **").append(subConfig.getAgentId()).append("**");
            if (subConfig.getDescription() != null) {
                sb.append(": ").append(subConfig.getDescription());
            }
            sb.append("\n");
        }
        sb.append("\n请根据用户请求的内容，选择最合适的子代理工具来处理。\n");

        String customPrompt = config.getSystemPrompt();
        if (customPrompt != null && !customPrompt.isBlank()) {
            sb.append("\n## 额外指示\n");
            sb.append(customPrompt).append("\n");
        }

        return sb.toString();
    }

    public ReActAgent createHandoffsAgent(AgentConfig config, Session session, Hook... hooks) {
        if (config.getSubAgents() == null || config.getSubAgents().isEmpty()) {
            throw new IllegalArgumentException("HANDOFFS agent requires at least one sub-agent: " + config.getAgentId());
        }

        log.info("Creating HANDOFFS agent for: {} with {} sub-agents and {} triggers",
                config.getAgentId(), config.getSubAgents().size(),
                config.getHandoffTriggers() != null ? config.getHandoffTriggers().size() : 0);

        Session effectiveSession = session != null ? session : new InMemorySession();

        String handoffsPrompt = buildHandoffsSystemPrompt(config);

        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.getModelName())
                .stream(config.isStreaming())
                .enableThinking(config.isEnableThinking())
                .formatter(new DashScopeChatFormatter())
                .build();

        Toolkit toolkit = new Toolkit();

        for (SubAgentConfig subConfig : config.getSubAgents()) {
            AgentConfig subAgentConfig = configService.getAgentConfig(subConfig.getAgentId());

            String originalPrompt = subAgentConfig != null ? subAgentConfig.getSystemPrompt() : "";
            String modifiedPrompt = "你是一个子代理，正在通过工具调用被主代理调用。\n" +
                    "请直接回答用户的问题，不要调用任何工具。\n" +
                    "专注于你作为" + subConfig.getAgentId() + "的专业领域。\n\n" +
                    "你的原始角色描述:\n" + originalPrompt;

            DashScopeChatModel subModel = DashScopeChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(subAgentConfig != null ? subAgentConfig.getModelName() : config.getModelName())
                    .stream(config.isStreaming())
                    .enableThinking(config.isEnableThinking())
                    .formatter(new DashScopeChatFormatter())
                    .build();

            ReActAgent subAgent = ReActAgent.builder()
                    .name(subConfig.getAgentId())
                    .sysPrompt(modifiedPrompt)
                    .model(subModel)
                    .session(new InMemorySession())
                    .sessionKey(SimpleSessionKey.of(subConfig.getAgentId()))
                    .toolkit(new Toolkit())
                    .enablePendingToolRecovery(true)
                    .build();

            SubAgentProvider<ReActAgent> provider = () -> subAgent;

            io.agentscope.core.tool.subagent.SubAgentConfig frameworkSubConfig =
                    io.agentscope.core.tool.subagent.SubAgentConfig.builder()
                            .toolName(subConfig.getAgentId())
                            .description(subConfig.getDescription() != null
                                    ? subConfig.getDescription()
                                    : "Sub-agent: " + subConfig.getAgentId())
                            .forwardEvents(true)
                            .streamOptions(StreamOptions.builder()
                                    .eventTypes(io.agentscope.core.agent.EventType.REASONING,
                                            io.agentscope.core.agent.EventType.TOOL_RESULT)
                                    .incremental(true)
                                    .includeReasoningResult(true)
                                    .build())
                            .build();

            SubAgentTool subAgentTool = new SubAgentTool(provider, frameworkSubConfig);
            toolkit.registerTool(subAgentTool);
            log.info("  Registered SubAgentTool: {} for handoffs agent: {} (no-tool mode)",
                    subConfig.getAgentId(), config.getAgentId());
        }

        ReActAgent.Builder builder = ReActAgent.builder()
                .name(config.getName() != null ? config.getName() : config.getAgentId())
                .sysPrompt(handoffsPrompt)
                .model(model)
                .session(effectiveSession)
                .sessionKey(SimpleSessionKey.of(config.getAgentId()))
                .toolkit(toolkit);

        if (hooks != null && hooks.length > 0) {
            builder.hooks(List.of(hooks));
        }

        return builder.build();
    }

    /**
     * @deprecated Use {@link #createHandoffsAgent(AgentConfig, Session, Hook...)} instead.
     */
    @Deprecated
    public ReActAgent createHandoffsAgent(AgentConfig config, io.agentscope.core.memory.Memory memory, Hook... hooks) {
        return createHandoffsAgent(config, (Session) null, hooks);
    }

    private String buildHandoffsSystemPrompt(AgentConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个智能代理协调器，负责根据用户意图将请求转交给合适的子代理处理。\n\n");
        sb.append("## 你的职责\n");
        sb.append("分析用户的消息，根据预定义的触发规则和子代理能力，选择最合适的子代理。\n\n");

        if (config.getDescription() != null && !config.getDescription().isBlank()) {
            sb.append("## 协调器描述\n");
            sb.append(config.getDescription()).append("\n\n");
        }

        if (config.getHandoffTriggers() != null && !config.getHandoffTriggers().isEmpty()) {
            sb.append("## 转交触发规则\n");
            sb.append("以下规则帮助你决定何时将请求转交给特定的子代理：\n\n");
            for (HandoffTrigger trigger : config.getHandoffTriggers()) {
                sb.append("- **转交目标**: ").append(trigger.getTarget()).append("\n");
                sb.append("  - **触发类型**: ").append(trigger.getType().getDescription()).append("\n");
                if (trigger.getKeywords() != null && !trigger.getKeywords().isEmpty()) {
                    sb.append("  - **触发关键词**: ");
                    sb.append(String.join(", ", trigger.getKeywords()));
                    sb.append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("## 可用的子代理\n");
        for (SubAgentConfig subConfig : config.getSubAgents()) {
            sb.append("- **").append(subConfig.getAgentId()).append("**");
            if (subConfig.getDescription() != null) {
                sb.append(": ").append(subConfig.getDescription());
            }
            sb.append("\n");
        }
        sb.append("\n请根据触发规则和用户请求的内容，选择最合适的子代理工具来处理请求。\n");

        String customPrompt = config.getSystemPrompt();
        if (customPrompt != null && !customPrompt.isBlank()) {
            sb.append("\n## 额外指示\n");
            sb.append(customPrompt).append("\n");
        }

        return sb.toString();
    }
}

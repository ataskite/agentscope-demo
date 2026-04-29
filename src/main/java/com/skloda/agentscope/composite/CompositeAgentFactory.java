package com.skloda.agentscope.composite;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentFactory;
import com.skloda.agentscope.agent.AgentType;
import com.skloda.agentscope.agent.HandoffTrigger;
import com.skloda.agentscope.agent.SubAgentConfig;
import com.skloda.agentscope.agent.TriggerType;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.pipeline.FanoutPipeline;
import io.agentscope.core.pipeline.SequentialPipeline;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.subagent.SubAgentProvider;
import io.agentscope.core.tool.subagent.SubAgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating multi-agent compositions.
 * Dispatches by AgentConfig.type() to the appropriate creation strategy.
 *
 * SINGLE agents delegate to the existing AgentFactory.
 * SEQUENTIAL/PARALLEL agents use AgentScope pipelines.
 * ROUTING/HANDOFFS agents use SubAgentTool for dynamic dispatch.
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

    /**
     * Create a single (SINGLE-type) agent using the existing AgentFactory.
     * Delegates to AgentFactory.createAgent(agentId, hooks).
     */
    public ReActAgent createSingleAgent(String agentId, Hook... hooks) {
        return singleAgentFactory.createAgent(agentId, hooks);
    }

    /**
     * Create a single agent for session use (with externally provided memory).
     */
    public ReActAgent createSingleAgentForSession(String agentId, Memory memory, Hook... hooks) {
        return singleAgentFactory.createAgentForSession(agentId, memory, hooks);
    }

    /**
     * Create memory for a given agentId.
     */
    public Memory createMemory(String agentId) {
        return singleAgentFactory.createMemory(agentId);
    }

    /**
     * Create sub-agents as AgentBase list for pipeline use.
     * Each sub-agent is created using AgentFactory with a fresh InMemoryMemory.
     */
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

    /**
     * Create sub-agents with shared memory for pipeline use.
     */
    public List<AgentBase> createSubAgentsWithMemory(AgentConfig config, Memory memory) {
        return config.getSubAgents().stream()
                .map(sub -> {
                    log.info("Creating sub-agent: {} for composite: {} (shared memory)", sub.getAgentId(), config.getAgentId());
                    return singleAgentFactory.createAgentForSession(sub.getAgentId(), memory);
                })
                .map(ReActAgent.class::cast)
                .map(AgentBase.class::cast)
                .toList();
    }

    // Stub methods for Tasks 11-13
    /**
     * Create a sequential pipeline of sub-agents.
     * Each sub-agent processes the message in order; the output of one feeds into the next.
     */
    public SequentialPipeline createSequentialAgent(AgentConfig config, Memory memory) {
        if (config.getSubAgents() == null || config.getSubAgents().isEmpty()) {
            throw new IllegalArgumentException("SEQUENTIAL agent requires at least one sub-agent: " + config.getAgentId());
        }

        log.info("Creating SEQUENTIAL pipeline for: {} with {} sub-agents", config.getAgentId(), config.getSubAgents().size());

        List<AgentBase> subAgents = (memory != null)
                ? createSubAgentsWithMemory(config, memory)
                : createSubAgents(config);

        return SequentialPipeline.builder()
                .addAgents(subAgents)
                .build();
    }

    /**
     * Create a parallel (fanout) pipeline of sub-agents.
     * All sub-agents receive the same message and execute concurrently.
     * Uses config.parallel flag to determine concurrent vs sequential fanout.
     */
    public FanoutPipeline createParallelAgent(AgentConfig config, Memory memory) {
        if (config.getSubAgents() == null || config.getSubAgents().isEmpty()) {
            throw new IllegalArgumentException("PARALLEL agent requires at least one sub-agent: " + config.getAgentId());
        }

        boolean concurrent = config.getParallel() != null ? config.getParallel() : true;
        log.info("Creating PARALLEL pipeline for: {} with {} sub-agents (concurrent={})",
                config.getAgentId(), config.getSubAgents().size(), concurrent);

        List<AgentBase> subAgents = (memory != null)
                ? createSubAgentsWithMemory(config, memory)
                : createSubAgents(config);

        FanoutPipeline.Builder builder = FanoutPipeline.builder()
                .addAgents(subAgents)
                .concurrent(concurrent);

        return builder.build();
    }

    /**
     * Create a routing agent that uses LLM to decide which sub-agent to dispatch to.
     * Each sub-agent is registered as a SubAgentTool, and the router's system prompt
     * describes each sub-agent's capabilities for intelligent routing.
     */
    public ReActAgent createRoutingAgent(AgentConfig config, Memory memory, Hook... hooks) {
        if (config.getSubAgents() == null || config.getSubAgents().isEmpty()) {
            throw new IllegalArgumentException("ROUTING agent requires at least one sub-agent: " + config.getAgentId());
        }

        log.info("Creating ROUTING agent for: {} with {} sub-agents", config.getAgentId(), config.getSubAgents().size());

        Memory effectiveMemory = memory != null ? memory : new InMemoryMemory();

        // Build routing system prompt
        String routingPrompt = buildRoutingSystemPrompt(config);

        // Create model for the router
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.getModelName())
                .stream(config.isStreaming())
                .enableThinking(config.isEnableThinking())
                .formatter(new DashScopeChatFormatter())
                .build();

        // Create toolkit with SubAgentTools
        Toolkit toolkit = new Toolkit();
        List<ReActAgent> subAgents = new ArrayList<>();

        for (SubAgentConfig subConfig : config.getSubAgents()) {
            // For ROUTING agents, create sub-agents without tools to avoid tool call conflicts
            AgentConfig subAgentConfig = configService.getAgentConfig(subConfig.getAgentId());

            // Build a modified system prompt that instructs the sub-agent to respond directly
            // without calling tools, since it's being invoked as a sub-agent tool
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

            // Create sub-agent with modified prompt and no toolkit
            ReActAgent subAgent = ReActAgent.builder()
                    .name(subConfig.getAgentId())
                    .sysPrompt(modifiedPrompt)
                    .model(subModel)
                    .memory(effectiveMemory)
                    .toolkit(new Toolkit()) // Empty toolkit - no tools for sub-agents in ROUTING
                    .build();

            subAgents.add(subAgent);

            // Create SubAgentProvider for this sub-agent
            SubAgentProvider<ReActAgent> provider = () -> subAgent;

            // Build SubAgentConfig for the framework
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

        // Build the router agent
        ReActAgent.Builder builder = ReActAgent.builder()
                .name(config.getName() != null ? config.getName() : config.getAgentId())
                .sysPrompt(routingPrompt)
                .model(model)
                .memory(effectiveMemory)
                .toolkit(toolkit);

        if (hooks != null && hooks.length > 0) {
            builder.hooks(List.of(hooks));
        }

        return builder.build();
    }

    /**
     * Build a routing system prompt that describes each sub-agent's capabilities.
     */
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

    /**
     * Create a handoffs agent with intent-based triggers.
     * Built on top of the routing agent pattern, but enhanced with explicit
     * handoff trigger rules that guide the LLM's routing decisions.
     */
    public ReActAgent createHandoffsAgent(AgentConfig config, Memory memory, Hook... hooks) {
        if (config.getSubAgents() == null || config.getSubAgents().isEmpty()) {
            throw new IllegalArgumentException("HANDOFFS agent requires at least one sub-agent: " + config.getAgentId());
        }

        log.info("Creating HANDOFFS agent for: {} with {} sub-agents and {} triggers",
                config.getAgentId(), config.getSubAgents().size(),
                config.getHandoffTriggers() != null ? config.getHandoffTriggers().size() : 0);

        Memory effectiveMemory = memory != null ? memory : new InMemoryMemory();

        // Build handoffs system prompt with trigger rules
        String handoffsPrompt = buildHandoffsSystemPrompt(config);

        // Create model for the handoffs agent
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.getModelName())
                .stream(config.isStreaming())
                .enableThinking(config.isEnableThinking())
                .formatter(new DashScopeChatFormatter())
                .build();

        // Create toolkit with SubAgentTools
        Toolkit toolkit = new Toolkit();

        for (SubAgentConfig subConfig : config.getSubAgents()) {
            // For HANDOFFS agents, create sub-agents without tools to avoid tool call conflicts
            AgentConfig subAgentConfig = configService.getAgentConfig(subConfig.getAgentId());

            // Build a modified system prompt that instructs the sub-agent to respond directly
            // without calling tools, since it's being invoked as a sub-agent tool
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

            // Create sub-agent with modified prompt and no toolkit
            ReActAgent subAgent = ReActAgent.builder()
                    .name(subConfig.getAgentId())
                    .sysPrompt(modifiedPrompt)
                    .model(subModel)
                    .memory(effectiveMemory)
                    .toolkit(new Toolkit()) // Empty toolkit - no tools for sub-agents in HANDOFFS
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

        // Build the handoffs agent
        ReActAgent.Builder builder = ReActAgent.builder()
                .name(config.getName() != null ? config.getName() : config.getAgentId())
                .sysPrompt(handoffsPrompt)
                .model(model)
                .memory(effectiveMemory)
                .toolkit(toolkit);

        if (hooks != null && hooks.length > 0) {
            builder.hooks(List.of(hooks));
        }

        return builder.build();
    }

    /**
     * Build a handoffs system prompt with explicit trigger rules.
     * Includes both the trigger-based routing rules and sub-agent descriptions.
     */
    private String buildHandoffsSystemPrompt(AgentConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个智能代理协调器，负责根据用户意图将请求转交给合适的子代理处理。\n\n");
        sb.append("## 你的职责\n");
        sb.append("分析用户的消息，根据预定义的触发规则和子代理能力，选择最合适的子代理。\n\n");

        if (config.getDescription() != null && !config.getDescription().isBlank()) {
            sb.append("## 协调器描述\n");
            sb.append(config.getDescription()).append("\n\n");
        }

        // Handoff trigger rules
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

        // Sub-agent descriptions
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

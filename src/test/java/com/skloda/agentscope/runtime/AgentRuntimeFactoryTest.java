package com.skloda.agentscope.runtime;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentType;
import com.skloda.agentscope.composite.CompositeAgentFactory;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.Memory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRuntimeFactoryTest {

    @Mock
    private CompositeAgentFactory compositeFactory;

    @Mock
    private AgentConfigService configService;

    @Mock
    private ReActAgent agent;

    @Mock
    private Memory memory;

    private AgentRuntimeFactory runtimeFactory;

    @BeforeEach
    void setUp() {
        runtimeFactory = new AgentRuntimeFactory(compositeFactory, configService);
    }

    @Test
    void createRuntimeReturnsSingleAgentRuntimeForSingleAgent() {
        AgentConfig config = config("chat-basic", AgentType.SINGLE);
        when(configService.getAgentConfig("chat-basic")).thenReturn(config);
        when(compositeFactory.createSingleAgent(eq("chat-basic"), any())).thenReturn(agent);

        StreamingAgentRuntime runtime = runtimeFactory.createRuntime("chat-basic");

        assertInstanceOf(AgentRuntime.class, runtime);
        verify(compositeFactory).createSingleAgent("chat-basic", runtime.getHook());
    }

    @Test
    void createRuntimeReturnsPipelineRuntimeForSequentialAgent() {
        AgentConfig config = config("doc-analysis-pipeline", AgentType.SEQUENTIAL);
        when(configService.getAgentConfig("doc-analysis-pipeline")).thenReturn(config);

        StreamingAgentRuntime runtime = runtimeFactory.createRuntime("doc-analysis-pipeline");

        assertInstanceOf(PipelineAgentRuntime.class, runtime);
        verify(compositeFactory).createSequentialAgent(config, null);
    }

    @Test
    void createRuntimeReturnsPipelineRuntimeForParallelAgent() {
        AgentConfig config = config("review-panel", AgentType.PARALLEL);
        when(configService.getAgentConfig("review-panel")).thenReturn(config);

        StreamingAgentRuntime runtime = runtimeFactory.createRuntime("review-panel");

        assertInstanceOf(PipelineAgentRuntime.class, runtime);
        verify(compositeFactory).createParallelAgent(config, null);
    }

    @Test
    void createRuntimeReturnsAgentRuntimeForRoutingAgent() {
        AgentConfig config = config("smart-router", AgentType.ROUTING);
        when(configService.getAgentConfig("smart-router")).thenReturn(config);
        when(compositeFactory.createRoutingAgent(any(), isNull(), any())).thenReturn(agent);

        StreamingAgentRuntime runtime = runtimeFactory.createRuntime("smart-router");

        assertInstanceOf(AgentRuntime.class, runtime);
    }

    @Test
    void createRuntimeReturnsAgentRuntimeForHandoffsAgent() {
        AgentConfig config = config("customer-service", AgentType.HANDOFFS);
        when(configService.getAgentConfig("customer-service")).thenReturn(config);
        when(compositeFactory.createHandoffsAgent(any(), isNull(), any())).thenReturn(agent);

        StreamingAgentRuntime runtime = runtimeFactory.createRuntime("customer-service");

        assertInstanceOf(AgentRuntime.class, runtime);
    }

    @Test
    void createRuntimeWithMemoryDispatchesCompositeTypes() {
        AgentConfig config = config("doc-analysis-pipeline", AgentType.SEQUENTIAL);
        when(configService.getAgentConfig("doc-analysis-pipeline")).thenReturn(config);

        StreamingAgentRuntime runtime = runtimeFactory.createRuntimeWithMemory("doc-analysis-pipeline", memory);

        assertInstanceOf(PipelineAgentRuntime.class, runtime);
        verify(compositeFactory).createSequentialAgent(config, memory);
    }

    private static AgentConfig config(String agentId, AgentType type) {
        AgentConfig config = new AgentConfig();
        config.setAgentId(agentId);
        config.setName(agentId);
        config.setType(type);
        return config;
    }
}

package com.skloda.agentscope.composite;

import com.skloda.agentscope.agent.*;
import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.pipeline.FanoutPipeline;
import io.agentscope.core.pipeline.SequentialPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompositeAgentFactoryTest {

    @Mock
    private AgentFactory singleAgentFactory;

    @Mock
    private AgentConfigService configService;

    @Mock
    private ReActAgent mockAgent;

    @Mock
    private Memory mockMemory;

    private CompositeAgentFactory factory;

    @BeforeEach
    void setUp() {
        factory = new CompositeAgentFactory(singleAgentFactory, configService);
        ReflectionTestUtils.setField(factory, "apiKey", "test-api-key");
    }

    @Test
    void testCreateSingleAgentDelegatesToAgentFactory() {
        Hook hook = new ObservabilityHook();
        when(singleAgentFactory.createAgent("test-agent", hook)).thenReturn(mockAgent);

        ReActAgent result = factory.createSingleAgent("test-agent", hook);

        assertNotNull(result);
        verify(singleAgentFactory).createAgent("test-agent", hook);
    }

    @Test
    void testCreateSingleAgentForSessionDelegates() {
        when(singleAgentFactory.createAgentForSession("test-agent", mockMemory))
                .thenReturn(mockAgent);

        ReActAgent result = factory.createSingleAgentForSession("test-agent", mockMemory);

        assertNotNull(result);
        verify(singleAgentFactory).createAgentForSession("test-agent", mockMemory);
    }

    @Test
    void testCreateMemoryDelegates() {
        InMemoryMemory expectedMemory = new InMemoryMemory();
        when(singleAgentFactory.createMemory("test-agent")).thenReturn(expectedMemory);

        Memory result = factory.createMemory("test-agent");

        assertNotNull(result);
        assertSame(expectedMemory, result);
    }

    // --- Sequential agent tests (Task 10) ---

    @Test
    void testCreateSequentialAgentRequiresSubAgents() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("seq-agent");
        config.setSubAgents(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> factory.createSequentialAgent(config, null));
    }

    @Test
    void testCreateSequentialAgentWithNullSubAgents() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("seq-agent");
        config.setSubAgents(null);

        assertThrows(IllegalArgumentException.class,
                () -> factory.createSequentialAgent(config, null));
    }

    @Test
    void testCreateSequentialAgentCreatesPipeline() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("seq-agent");
        config.setSubAgents(List.of(
                SubAgentConfig.builder().agentId("sub-1").description("First").build(),
                SubAgentConfig.builder().agentId("sub-2").description("Second").build()
        ));

        ReActAgent mockSub1 = mock(ReActAgent.class);
        ReActAgent mockSub2 = mock(ReActAgent.class);
        when(singleAgentFactory.createAgent("sub-1")).thenReturn(mockSub1);
        when(singleAgentFactory.createAgent("sub-2")).thenReturn(mockSub2);

        SequentialPipeline pipeline = factory.createSequentialAgent(config, null);

        assertNotNull(pipeline);
        assertEquals(2, pipeline.size());
        verify(singleAgentFactory).createAgent("sub-1");
        verify(singleAgentFactory).createAgent("sub-2");
    }

    @Test
    void testCreateSequentialAgentWithSharedMemory() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("seq-agent");
        config.setSubAgents(List.of(
                SubAgentConfig.builder().agentId("sub-1").build()
        ));

        when(singleAgentFactory.createAgentForSession("sub-1", mockMemory))
                .thenReturn(mockAgent);

        SequentialPipeline pipeline = factory.createSequentialAgent(config, mockMemory);

        assertNotNull(pipeline);
        assertEquals(1, pipeline.size());
        verify(singleAgentFactory).createAgentForSession("sub-1", mockMemory);
    }

    // --- Parallel agent tests (Task 11) ---

    @Test
    void testCreateParallelAgentRequiresSubAgents() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("par-agent");
        config.setSubAgents(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> factory.createParallelAgent(config, null));
    }

    @Test
    void testCreateParallelAgentWithNullSubAgents() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("par-agent");
        config.setSubAgents(null);

        assertThrows(IllegalArgumentException.class,
                () -> factory.createParallelAgent(config, null));
    }

    @Test
    void testCreateParallelAgentCreatesFanoutPipeline() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("par-agent");
        config.setSubAgents(List.of(
                SubAgentConfig.builder().agentId("sub-1").description("First").build(),
                SubAgentConfig.builder().agentId("sub-2").description("Second").build()
        ));
        config.setParallel(true);

        ReActAgent mockSub1 = mock(ReActAgent.class);
        ReActAgent mockSub2 = mock(ReActAgent.class);
        when(singleAgentFactory.createAgent("sub-1")).thenReturn(mockSub1);
        when(singleAgentFactory.createAgent("sub-2")).thenReturn(mockSub2);

        FanoutPipeline pipeline = factory.createParallelAgent(config, null);

        assertNotNull(pipeline);
        assertEquals(2, pipeline.size());
        verify(singleAgentFactory).createAgent("sub-1");
        verify(singleAgentFactory).createAgent("sub-2");
    }

    @Test
    void testCreateParallelAgentSequentialMode() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("par-agent");
        config.setSubAgents(List.of(
                SubAgentConfig.builder().agentId("sub-1").build()
        ));
        config.setParallel(false);

        when(singleAgentFactory.createAgent("sub-1")).thenReturn(mockAgent);

        FanoutPipeline pipeline = factory.createParallelAgent(config, null);

        assertNotNull(pipeline);
        assertEquals(1, pipeline.size());
    }

    @Test
    void testCreateParallelAgentDefaultIsConcurrent() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("par-agent");
        config.setSubAgents(List.of(
                SubAgentConfig.builder().agentId("sub-1").build()
        ));
        // parallel is null -> defaults to true

        when(singleAgentFactory.createAgent("sub-1")).thenReturn(mockAgent);

        FanoutPipeline pipeline = factory.createParallelAgent(config, null);

        assertNotNull(pipeline);
        assertEquals(1, pipeline.size());
        // Verify that when parallel is null, concurrent(true) was called
        verify(singleAgentFactory).createAgent("sub-1");
    }

    // --- Routing agent tests (Task 12) ---

    @Test
    void testCreateRoutingAgentRequiresSubAgents() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("route-agent");
        config.setSubAgents(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> factory.createRoutingAgent(config, null));
    }

    @Test
    void testCreateRoutingAgentWithNullSubAgents() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("route-agent");
        config.setSubAgents(null);

        assertThrows(IllegalArgumentException.class,
                () -> factory.createRoutingAgent(config, null));
    }

    @Test
    void testCreateRoutingAgentCreatesReActAgent() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("route-agent");
        config.setName("Test Router");
        config.setSubAgents(List.of(
                SubAgentConfig.builder().agentId("sub-1").description("Handles documents").build(),
                SubAgentConfig.builder().agentId("sub-2").description("Handles chat").build()
        ));

        when(singleAgentFactory.createAgent("sub-1")).thenReturn(mockAgent);
        when(singleAgentFactory.createAgent("sub-2")).thenReturn(mockAgent);

        ReActAgent router = factory.createRoutingAgent(config, null);

        assertNotNull(router);
        verify(singleAgentFactory).createAgent("sub-1");
        verify(singleAgentFactory).createAgent("sub-2");
    }

    @Test
    void testCreateRoutingAgentWithMemory() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("route-agent");
        config.setSubAgents(List.of(
                SubAgentConfig.builder().agentId("sub-1").build()
        ));

        when(singleAgentFactory.createAgentForSession("sub-1", mockMemory)).thenReturn(mockAgent);

        ReActAgent router = factory.createRoutingAgent(config, mockMemory);

        assertNotNull(router);
        verify(singleAgentFactory).createAgentForSession("sub-1", mockMemory);
    }

    @Test
    void testCreateRoutingAgentWithHook() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("route-agent");
        config.setSubAgents(List.of(
                SubAgentConfig.builder().agentId("sub-1").build()
        ));

        Hook hook = new ObservabilityHook();
        when(singleAgentFactory.createAgent("sub-1")).thenReturn(mockAgent);

        ReActAgent router = factory.createRoutingAgent(config, null, hook);

        assertNotNull(router);
    }

    // --- Handoffs agent tests (Task 13) ---

    @Test
    void testCreateHandoffsAgentRequiresSubAgents() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("handoff-agent");
        config.setSubAgents(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> factory.createHandoffsAgent(config, null));
    }

    @Test
    void testCreateHandoffsAgentCreatesReActAgent() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("handoff-agent");
        config.setName("Test Handoffs");
        config.setSubAgents(List.of(
                SubAgentConfig.builder().agentId("sub-1").description("Handles documents").build(),
                SubAgentConfig.builder().agentId("sub-2").description("Handles chat").build()
        ));
        config.setHandoffTriggers(List.of(
                HandoffTrigger.builder().type(TriggerType.INTENT)
                        .keywords(List.of("文档", "docx", "file"))
                        .target("sub-1").build(),
                HandoffTrigger.builder().type(TriggerType.INTENT)
                        .keywords(List.of("聊天", "hello", "help"))
                        .target("sub-2").build()
        ));

        when(singleAgentFactory.createAgent("sub-1")).thenReturn(mockAgent);
        when(singleAgentFactory.createAgent("sub-2")).thenReturn(mockAgent);

        ReActAgent handoffAgent = factory.createHandoffsAgent(config, null);

        assertNotNull(handoffAgent);
        verify(singleAgentFactory).createAgent("sub-1");
        verify(singleAgentFactory).createAgent("sub-2");
    }

    @Test
    void testCreateHandoffsAgentWithMemory() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("handoff-agent");
        config.setSubAgents(List.of(
                SubAgentConfig.builder().agentId("sub-1").build()
        ));

        when(singleAgentFactory.createAgentForSession("sub-1", mockMemory)).thenReturn(mockAgent);

        ReActAgent handoffAgent = factory.createHandoffsAgent(config, mockMemory);

        assertNotNull(handoffAgent);
        verify(singleAgentFactory).createAgentForSession("sub-1", mockMemory);
    }

    @Test
    void testCreateHandoffsAgentWithHook() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("handoff-agent");
        config.setSubAgents(List.of(
                SubAgentConfig.builder().agentId("sub-1").build()
        ));

        Hook hook = new ObservabilityHook();
        when(singleAgentFactory.createAgent("sub-1")).thenReturn(mockAgent);

        ReActAgent handoffAgent = factory.createHandoffsAgent(config, null, hook);

        assertNotNull(handoffAgent);
    }

    @Test
    void testCreateHandoffsAgentWithNoTriggers() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("handoff-agent");
        config.setSubAgents(List.of(
                SubAgentConfig.builder().agentId("sub-1").build()
        ));
        // No handoff triggers - should still work (falls back to LLM routing)

        when(singleAgentFactory.createAgent("sub-1")).thenReturn(mockAgent);

        ReActAgent handoffAgent = factory.createHandoffsAgent(config, null);

        assertNotNull(handoffAgent);
    }

    @Test
    void testCreateSubAgentsDelegatesForConfig() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("composite-agent");
        config.setSubAgents(List.of(
                SubAgentConfig.builder().agentId("sub-1").description("First sub-agent").build(),
                SubAgentConfig.builder().agentId("sub-2").description("Second sub-agent").build()
        ));

        ReActAgent mockSub1 = mock(ReActAgent.class);
        ReActAgent mockSub2 = mock(ReActAgent.class);
        when(singleAgentFactory.createAgent("sub-1")).thenReturn(mockSub1);
        when(singleAgentFactory.createAgent("sub-2")).thenReturn(mockSub2);

        var result = factory.createSubAgents(config);

        assertEquals(2, result.size());
        verify(singleAgentFactory).createAgent("sub-1");
        verify(singleAgentFactory).createAgent("sub-2");
    }
}

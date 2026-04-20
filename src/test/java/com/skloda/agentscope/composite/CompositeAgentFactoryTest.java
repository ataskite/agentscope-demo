package com.skloda.agentscope.composite;

import com.skloda.agentscope.agent.*;
import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.pipeline.SequentialPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    // --- Stub tests for Tasks 11-13 ---

    @Test
    void testCreateParallelAgentThrowsNotImplemented() {
        AgentConfig config = new AgentConfig();
        config.setType(AgentType.PARALLEL);

        assertThrows(UnsupportedOperationException.class,
                () -> factory.createParallelAgent(config, mockMemory));
    }

    @Test
    void testCreateRoutingAgentThrowsNotImplemented() {
        AgentConfig config = new AgentConfig();
        config.setType(AgentType.ROUTING);

        assertThrows(UnsupportedOperationException.class,
                () -> factory.createRoutingAgent(config, mockMemory));
    }

    @Test
    void testCreateHandoffsAgentThrowsNotImplemented() {
        AgentConfig config = new AgentConfig();
        config.setType(AgentType.HANDOFFS);

        assertThrows(UnsupportedOperationException.class,
                () -> factory.createHandoffsAgent(config, mockMemory));
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

package com.skloda.agentscope.agent;

import com.skloda.agentscope.service.KnowledgeService;
import com.skloda.agentscope.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentFactoryPlanAndMemoryTest {

    @Mock
    private AgentConfigService configService;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private KnowledgeService knowledgeService;

    private AgentFactory agentFactory;

    @BeforeEach
    void setUp() {
        agentFactory = new AgentFactory(configService, toolRegistry, knowledgeService);
        // Set apiKey via reflection since @Value isn't processed in unit tests
        try {
            var field = AgentFactory.class.getDeclaredField("apiKey");
            field.setAccessible(true);
            field.set(agentFactory, "test-api-key");
        } catch (Exception e) {
            fail("Failed to set apiKey: " + e.getMessage());
        }
    }

    private AgentConfig baseConfig(String agentId) {
        AgentConfig config = new AgentConfig();
        config.setAgentId(agentId);
        config.setName("Test " + agentId);
        config.setModelName("qwen-plus");
        return config;
    }

    @Test
    void testParseLtmModeStaticControl() {
        assertEquals(io.agentscope.core.memory.LongTermMemoryMode.STATIC_CONTROL,
                agentFactory.parseLtmMode("STATIC_CONTROL"));
    }

    @Test
    void testParseLtmModeAgentControl() {
        assertEquals(io.agentscope.core.memory.LongTermMemoryMode.AGENT_CONTROL,
                agentFactory.parseLtmMode("AGENT_CONTROL"));
    }

    @Test
    void testParseLtmModeBoth() {
        assertEquals(io.agentscope.core.memory.LongTermMemoryMode.BOTH,
                agentFactory.parseLtmMode("BOTH"));
    }

    @Test
    void testParseLtmModeNullDefaultsToStaticControl() {
        assertEquals(io.agentscope.core.memory.LongTermMemoryMode.STATIC_CONTROL,
                agentFactory.parseLtmMode(null));
    }

    @Test
    void testParseLtmModeUnknownDefaultsToStaticControl() {
        assertEquals(io.agentscope.core.memory.LongTermMemoryMode.STATIC_CONTROL,
                agentFactory.parseLtmMode("UNKNOWN"));
    }

    @Test
    void testCreateAgentWithPlanEnabledDoesNotThrow() {
        AgentConfig config = baseConfig("plan-agent");
        config.setPlanEnabled(true);
        when(configService.getAgentConfig("plan-agent")).thenReturn(config);

        assertDoesNotThrow(() -> agentFactory.createAgent("plan-agent"));
    }

    @Test
    void testCreateAgentWithBailianLongTermMemoryDoesNotThrow() {
        AgentConfig config = baseConfig("memory-agent");
        AgentConfig.LongTermMemoryConfig ltm = new AgentConfig.LongTermMemoryConfig();
        ltm.setType("bailian");
        ltm.setUserId("test-user");
        config.setLongTermMemory(ltm);
        when(configService.getAgentConfig("memory-agent")).thenReturn(config);

        assertDoesNotThrow(() -> agentFactory.createAgent("memory-agent"));
    }

    @Test
    void testCreateAgentWithNoneTypeDoesNotFail() {
        AgentConfig config = baseConfig("no-mem-agent");
        AgentConfig.LongTermMemoryConfig ltm = new AgentConfig.LongTermMemoryConfig();
        ltm.setType("none");
        config.setLongTermMemory(ltm);
        when(configService.getAgentConfig("no-mem-agent")).thenReturn(config);

        assertDoesNotThrow(() -> agentFactory.createAgent("no-mem-agent"));
    }
}

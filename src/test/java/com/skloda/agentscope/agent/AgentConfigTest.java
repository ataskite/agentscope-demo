package com.skloda.agentscope.agent;

import com.skloda.agentscope.mcp.McpServerRef;
import com.skloda.agentscope.mcp.ToolGroupConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class AgentConfigTest {
    @Test
    void testDefaultTypeIsSingle() {
        AgentConfig config = new AgentConfig();
        assertEquals(AgentType.SINGLE, config.getType());
    }

    @Test
    void testMultiAgentFields() {
        AgentConfig config = new AgentConfig();
        config.setType(AgentType.SEQUENTIAL);
        config.setSubAgents(List.of(
            SubAgentConfig.builder().agentId("agent1").description("First").build()
        ));
        config.setParallel(false);

        assertEquals(AgentType.SEQUENTIAL, config.getType());
        assertEquals(1, config.getSubAgents().size());
        assertFalse(config.getParallel());
    }

    @Test
    void testDefaultRagModeIsGeneric() {
        AgentConfig config = new AgentConfig();
        assertEquals("generic", config.getRagMode());
    }

    @Test
    void testRagModeCanBeSet() {
        AgentConfig config = new AgentConfig();
        config.setRagMode("agentic");
        assertEquals("agentic", config.getRagMode());
    }

    @Test
    void testMcpServersField() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("test-mcp");

        List<McpServerRef> refs = List.of(
            McpServerRef.builder()
                .server("filesystem-local")
                .enableTools(List.of("read_file"))
                .build()
        );

        config.setMcpServers(refs);

        assertEquals(1, config.getMcpServers().size());
        assertEquals("filesystem-local", config.getMcpServers().get(0).getServer());
    }

    @Test
    void testToolGroupsField() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("test-groups");

        List<ToolGroupConfig> groups = List.of(
            ToolGroupConfig.builder()
                .name("filesystem")
                .description("文件操作工具")
                .active(true)
                .build()
        );

        config.setToolGroups(groups);

        assertEquals(1, config.getToolGroups().size());
        assertEquals("filesystem", config.getToolGroups().get(0).getName());
    }
}

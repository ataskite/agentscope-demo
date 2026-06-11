package com.skloda.agentscope.permission;

import com.skloda.agentscope.agent.AgentConfig;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PermissionContextFactoryTest {

    private final PermissionContextFactory factory = new PermissionContextFactory();

    @Test
    void bypassModeAllowsAll() {
        PermissionContextState ctx = factory.build("bypass", null);
        assertEquals(PermissionMode.BYPASS, ctx.getMode());
    }

    @Test
    void exploreModeSetsExplore() {
        PermissionContextState ctx = factory.build("explore", null);
        assertEquals(PermissionMode.EXPLORE, ctx.getMode());
    }

    @Test
    void acceptEditsModeWithAskRules() {
        AgentConfig.PermissionConfig config = new AgentConfig.PermissionConfig();
        config.setAskTools(List.of("execute_shell_command"));

        PermissionContextState ctx = factory.build("accept_edits", config);
        assertEquals(PermissionMode.ACCEPT_EDITS, ctx.getMode());
        assertFalse(ctx.getAskRules().isEmpty());
        assertTrue(ctx.getAskRules().containsKey("execute_shell_command"));
    }

    @Test
    void denyToolsAppliedRegardlessOfMode() {
        AgentConfig.PermissionConfig config = new AgentConfig.PermissionConfig();
        config.setDenyTools(List.of("write_text_file"));

        PermissionContextState ctx = factory.build("bypass", config);
        assertEquals(PermissionMode.BYPASS, ctx.getMode());
        assertFalse(ctx.getDenyRules().isEmpty());
        assertTrue(ctx.getDenyRules().containsKey("write_text_file"));
    }

    @Test
    void nullConfigUsesModeOnly() {
        PermissionContextState ctx = factory.build("explore", null);
        assertEquals(PermissionMode.EXPLORE, ctx.getMode());
        assertTrue(ctx.getAllowRules().isEmpty());
        assertTrue(ctx.getDenyRules().isEmpty());
        assertTrue(ctx.getAskRules().isEmpty());
    }
}

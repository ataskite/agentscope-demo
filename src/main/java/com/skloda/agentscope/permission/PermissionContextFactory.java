package com.skloda.agentscope.permission;

import com.skloda.agentscope.agent.AgentConfig;
import io.agentscope.core.permission.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PermissionContextFactory {

    /**
     * Framework tools that must never run without an explicit, configured permission
     * policy. Used by {@link #buildFailClosed()} as defense-in-depth for agents that
     * mount system tools without any {@code permissionConfig}.
     */
    private static final List<String> FAIL_CLOSED_DENY_TOOLS =
            List.of("execute_shell_command", "write_text_file", "edit_docx");

    public PermissionContextState build(String mode, AgentConfig.PermissionConfig config) {
        PermissionContextState.Builder builder = PermissionContextState.builder()
                .mode(PermissionMode.fromString(mode != null ? mode : "bypass"));

        if (config != null) {
            applyDenyRules(builder, config.getDenyTools());
            if ("accept_edits".equals(mode)) {
                applyAskRules(builder, config.getAskTools());
            }
        }

        return builder.build();
    }

    /**
     * P0-1/P1-1 security fix: fail-closed permission context injected when an agent
     * mounts system tools without any {@code permissionConfig}. EXPLORE mode blocks
     * mutations; the high-risk framework tools are additionally denied explicitly.
     */
    public PermissionContextState buildFailClosed() {
        PermissionContextState.Builder builder = PermissionContextState.builder()
                .mode(PermissionMode.EXPLORE);
        for (String tool : FAIL_CLOSED_DENY_TOOLS) {
            builder.addDenyRule(tool, new PermissionRule(tool, null, PermissionBehavior.DENY, "fail-closed-default"));
        }
        return builder.build();
    }

    private void applyDenyRules(PermissionContextState.Builder builder, List<String> denyTools) {
        if (denyTools == null) return;
        for (String tool : denyTools) {
            builder.addDenyRule(tool, new PermissionRule(tool, null, PermissionBehavior.DENY, "config"));
        }
    }

    private void applyAskRules(PermissionContextState.Builder builder, List<String> askTools) {
        if (askTools == null) return;
        for (String tool : askTools) {
            builder.addAskRule(tool, new PermissionRule(tool, null, PermissionBehavior.ASK, "config"));
        }
    }
}

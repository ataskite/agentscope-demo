package com.skloda.agentscope.permission;

import com.skloda.agentscope.agent.AgentConfig;
import io.agentscope.core.permission.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PermissionContextFactory {

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

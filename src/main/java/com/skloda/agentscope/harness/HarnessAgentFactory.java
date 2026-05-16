package com.skloda.agentscope.harness;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.HarnessConfig;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

public class HarnessAgentFactory {

    private static final Logger log = LoggerFactory.getLogger(HarnessAgentFactory.class);

    public static HarnessAgent create(AgentConfig config, String apiKey) throws Exception {
        HarnessConfig harnessConfig = config.getHarnessConfig();
        if (harnessConfig == null) {
            throw new IllegalArgumentException("Agent '" + config.getAgentId() + "' is type HARNESS but has no harnessConfig");
        }

        // 1. Resolve workspace path
        Path workspace = resolveWorkspace(harnessConfig.getWorkspace(), config.getAgentId());

        // 2. Initialize workspace from templates (idempotent)
        WorkspaceInitializer.initializeFromTemplates(config.getAgentId(), workspace);

        // Also initialize all subagent workspaces
        for (HarnessConfig.SubAgentRef sub : harnessConfig.getSubagents()) {
            Path subWorkspace = workspace.getParent().resolve(sub.getName());
            WorkspaceInitializer.initializeSubagentWorkspace(sub.getName(), subWorkspace);
        }

        // 3. Build model
        String modelName = config.getModelName() != null ? config.getModelName() : "qwen-max";
        Model model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .stream(true)
                .build();

        // 4. Build HarnessAgent
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(config.getName() != null ? config.getName() : config.getAgentId())
                .description(config.getDescription())
                .sysPrompt(config.getSystemPrompt())
                .model(model)
                .workspace(workspace);

        // 5. Configure compaction
        if (harnessConfig.getCompaction() != null) {
            HarnessConfig.CompactionConfig cc = harnessConfig.getCompaction();
            builder.compaction(CompactionConfig.builder()
                    .triggerMessages(cc.getTriggerMessages())
                    .keepMessages(cc.getKeepMessages())
                    .flushBeforeCompact(cc.isFlushBeforeCompact())
                    .build());
        }

        HarnessAgent agent = builder.build();
        log.info("HarnessAgent '{}' built with workspace={}", config.getAgentId(), workspace);
        return agent;
    }

    private static Path resolveWorkspace(String configuredPath, String agentId) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            String expanded = configuredPath.replace("${user.home}", System.getProperty("user.home"));
            return Paths.get(expanded);
        }
        return Paths.get(System.getProperty("user.home"), ".agentscope", agentId);
    }
}

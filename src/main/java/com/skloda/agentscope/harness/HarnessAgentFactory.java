package com.skloda.agentscope.harness;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.HarnessConfig;
import com.skloda.agentscope.model.ModelFactory;
import com.skloda.agentscope.permission.PermissionContextFactory;
import io.agentscope.core.model.Model;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class HarnessAgentFactory {

    private static final Logger log = LoggerFactory.getLogger(HarnessAgentFactory.class);

    private final FilesystemSpecFactory filesystemSpecFactory;
    private final CompactionConfigFactory compactionConfigFactory;
    private final ModelFactory modelFactory;
    private final PermissionContextFactory permissionContextFactory;

    public HarnessAgentFactory(FilesystemSpecFactory filesystemSpecFactory,
                               CompactionConfigFactory compactionConfigFactory,
                               ModelFactory modelFactory,
                               PermissionContextFactory permissionContextFactory) {
        this.filesystemSpecFactory = filesystemSpecFactory;
        this.compactionConfigFactory = compactionConfigFactory;
        this.modelFactory = modelFactory;
        this.permissionContextFactory = permissionContextFactory;
    }

    public HarnessAgent create(AgentConfig config, String apiKey, String executionModeOverride) throws Exception {
        HarnessConfig harnessConfig = config.getHarnessConfig();
        if (harnessConfig == null) {
            throw new IllegalArgumentException("Agent '" + config.getAgentId() + "' is type HARNESS but has no harnessConfig");
        }

        // Determine effective execution mode: override > config default
        boolean isBuilder;
        if (executionModeOverride != null && !executionModeOverride.isBlank()) {
            isBuilder = "BUILDER".equalsIgnoreCase(executionModeOverride);
        } else {
            isBuilder = harnessConfig.isBuilderMode();
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

        // 3. Build model via ModelRegistry (unified entry point)
        String modelName = config.getModelName() != null ? config.getModelName() : "qwen-max";
        boolean streaming = config.isStreaming();
        Model model = modelFactory.createModel(modelName, streaming, config.isEnableThinking());

        // 4. Build HarnessAgent
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(config.getName() != null ? config.getName() : config.getAgentId())
                .description(config.getDescription())
                .sysPrompt(config.getSystemPrompt())
                .model(model)
                .workspace(workspace);

        // 5. Configure filesystem (Docker sandbox / Local)
        if (harnessConfig.isDockerMode()) {
            DockerFilesystemSpec dockerSpec = filesystemSpecFactory.createDocker();
            // Apply sandbox config overrides if provided
            HarnessConfig.SandboxConfig sandbox = harnessConfig.getSandbox();
            if (sandbox != null) {
                if (sandbox.getImage() != null && !sandbox.getImage().isBlank()) {
                    dockerSpec.image(sandbox.getImage());
                }
                if (sandbox.getMemorySizeBytes() > 0) {
                    dockerSpec.memorySizeBytes(sandbox.getMemorySizeBytes());
                }
                if (sandbox.getCpuCount() > 0) {
                    dockerSpec.cpuCount(sandbox.getCpuCount());
                }
            }
            builder.filesystem(dockerSpec);
            String imageLog = (sandbox != null && sandbox.getImage() != null) ? sandbox.getImage() : "python:3.11-slim";
            log.info("Agent '{}' using DOCKER sandbox (image={})", config.getAgentId(), imageLog);
        } else if (isBuilder) {
            builder.filesystem(filesystemSpecFactory.createLocal());
            log.info("Agent '{}' using BUILDER mode with LocalFilesystemSpec", config.getAgentId());
        }

        // 6. Configure compaction
        if (harnessConfig.getCompaction() != null) {
            HarnessConfig.CompactionConfig cc = harnessConfig.getCompaction();
            builder.compaction(CompactionConfig.builder()
                    .triggerMessages(cc.getTriggerMessages())
                    .keepMessages(cc.getKeepMessages())
                    .flushBeforeCompact(cc.isFlushBeforeCompact())
                    .build());
        }

        // 7. Configure tool result eviction (wires the previously unused CompactionConfigFactory)
        ToolResultEvictionConfig evictionConfig = compactionConfigFactory.createEvictionConfig();
        builder.toolResultEviction(evictionConfig);
        log.info("Agent '{}' configured with ToolResultEvictionConfig (maxChars={})",
                config.getAgentId(), evictionConfig.getMaxResultChars());

        // 8. Configure Task List
        if (harnessConfig.isTaskListEnabled()) {
            builder.enableTaskList();
            log.info("Agent '{}' enabled Task List (TodoTools + TaskReminderMiddleware)", config.getAgentId());
        }

        // 9. Configure permission context (S6)
        AgentConfig.PermissionConfig permConfig = harnessConfig.getPermissionConfig();
        if (permConfig != null) {
            String mode = permConfig.getDefaultMode() != null ? permConfig.getDefaultMode() : "bypass";
            PermissionContextState permState = permissionContextFactory.build(mode, permConfig);
            builder.permissionContext(permState);
            log.info("Agent '{}' configured permission context (mode={})", config.getAgentId(), mode);
        }

        // 10. Configure skill repository (S6)
        if (harnessConfig.getSkillPath() != null && !harnessConfig.getSkillPath().isBlank()) {
            ClasspathSkillRepository skillRepo = new ClasspathSkillRepository(harnessConfig.getSkillPath());
            builder.skillRepository(skillRepo);
            log.info("Agent '{}' configured SkillRepository (path={})", config.getAgentId(), harnessConfig.getSkillPath());
        }

        // 11. Configure additional context files (S7)
        if (harnessConfig.getAdditionalContextFiles() != null) {
            for (String contextFile : harnessConfig.getAdditionalContextFiles()) {
                builder.additionalContextFile(contextFile);
            }
        }

        // 12. Configure max context tokens (S7)
        builder.maxContextTokens(harnessConfig.getMaxContextTokens());

        // 13. Configure meta tool (S7)
        if (harnessConfig.isMetaToolEnabled()) {
            builder.enableMetaTool(true);
            log.info("Agent '{}' enabled MetaTool (reset_tools)", config.getAgentId());
        }

        HarnessAgent agent = builder.build();
        log.info("HarnessAgent '{}' built with workspace={}, mode={}, docker={}",
                config.getAgentId(), workspace,
                isBuilder ? "BUILDER" : "CLAW", harnessConfig.isDockerMode());
        return agent;
    }

    public HarnessAgent create(AgentConfig config, String apiKey) throws Exception {
        return create(config, apiKey, null);
    }

    private Path resolveWorkspace(String configuredPath, String agentId) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            String expanded = configuredPath.replace("${user.home}", System.getProperty("user.home"));
            return Paths.get(expanded);
        }
        return Paths.get(System.getProperty("user.home"), ".agentscope", agentId);
    }
}

package com.skloda.agentscope.agent;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class HarnessConfig {

    private String workspace;
    private String filesystemMode = "LOCAL";
    private String executionMode = "CLAW";
    private String isolationScope = "USER";
    private CompactionConfig compaction;
    private List<SubAgentRef> subagents = new ArrayList<>();
    private boolean taskListEnabled = false;
    private SandboxConfig sandbox;

    public boolean isBuilderMode() {
        return "BUILDER".equalsIgnoreCase(executionMode);
    }

    public boolean isDockerMode() {
        return "DOCKER".equalsIgnoreCase(filesystemMode);
    }

    @Setter
    @Getter
    public static class CompactionConfig {
        private int triggerMessages = 30;
        private int keepMessages = 10;
        private boolean flushBeforeCompact = true;
    }

    @Setter
    @Getter
    public static class SubAgentRef {
        private String name;
        private String description;
    }

    @Setter
    @Getter
    public static class SandboxConfig {
        private String image = "python:3.11-slim";
        private long memorySizeBytes = 2_000_000_000L;
        private long cpuCount = 2L;
    }
}

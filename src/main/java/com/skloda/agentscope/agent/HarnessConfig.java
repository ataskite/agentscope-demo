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
    private CompactionConfig compaction;
    private List<SubAgentRef> subagents = new ArrayList<>();

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
}

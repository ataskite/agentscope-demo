package com.skloda.agentscope.harness;

import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig.TruncateArgsConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import org.springframework.stereotype.Component;

@Component
public class CompactionConfigFactory {

    public CompactionConfig createDefault() {
        return CompactionConfig.builder()
                .triggerMessages(50)
                .triggerTokens(100000)
                .keepMessages(10)
                .keepTokens(50000)
                .summaryPrompt("请总结以上对话的关键信息，保留重要的事实和决策。")
                .flushBeforeCompact(true)
                .offloadBeforeCompact(true)
                .truncateArgs(
                        TruncateArgsConfig.builder()
                                .maxArgLength(10000)
                                .truncationText("...[truncated]...")
                                .build()
                )
                .build();
    }

    public CompactionConfig createForDemo() {
        // Lower thresholds for demo purposes
        return CompactionConfig.builder()
                .triggerMessages(10)
                .triggerTokens(20000)
                .keepMessages(5)
                .keepTokens(10000)
                .summaryPrompt("总结关键信息。")
                .flushBeforeCompact(true)
                .build();
    }

    public ToolResultEvictionConfig createEvictionConfig() {
        return ToolResultEvictionConfig.builder()
                .maxResultChars(50000)
                .previewChars(1000)
                .build();
    }
}

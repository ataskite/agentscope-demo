package com.skloda.agentscope.service;

import io.agentscope.core.rag.reader.SplitStrategy;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "agentscope.knowledge")
public class KnowledgeProperties {

    private boolean enabled = true;
    private String path = "knowledge";
    private String embeddingModel = "text-embedding-v3";
    private int dimensions = 1024;
    private int chunkSize = 512;
    private int overlapSize = 50;
    private SplitStrategy splitStrategy = SplitStrategy.PARAGRAPH;
    private boolean autoIndexOnStartup = true;
}

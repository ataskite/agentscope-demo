package com.skloda.agentscope.service;

import io.agentscope.core.rag.reader.SplitStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeServiceStatusTest {

    @Test
    void knowledgePropertiesUseRequiredDefaults() {
        KnowledgeProperties properties = new KnowledgeProperties();

        assertTrue(properties.isEnabled());
        assertEquals("knowledge", properties.getPath());
        assertEquals("text-embedding-v3", properties.getEmbeddingModel());
        assertEquals(1024, properties.getDimensions());
        assertEquals(512, properties.getChunkSize());
        assertEquals(50, properties.getOverlapSize());
        assertEquals(SplitStrategy.PARAGRAPH, properties.getSplitStrategy());
        assertTrue(properties.isAutoIndexOnStartup());
    }
}

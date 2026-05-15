package com.skloda.agentscope.service;

import io.agentscope.core.rag.reader.SplitStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgePropertiesTest {

    @Test
    void constructor_createsWithDefaults() {
        KnowledgeProperties props = new KnowledgeProperties();

        assertTrue(props.isEnabled());
        assertEquals("knowledge", props.getPath());
        assertEquals("text-embedding-v3", props.getEmbeddingModel());
        assertEquals(1024, props.getDimensions());
        assertEquals(512, props.getChunkSize());
        assertEquals(50, props.getOverlapSize());
        assertEquals(SplitStrategy.PARAGRAPH, props.getSplitStrategy());
        assertTrue(props.isAutoIndexOnStartup());
    }

    @Test
    void setters_canModifyValues() {
        KnowledgeProperties props = new KnowledgeProperties();

        props.setEnabled(false);
        props.setPath("custom-path");
        props.setEmbeddingModel("text-embedding-v2");
        props.setDimensions(768);
        props.setChunkSize(256);
        props.setOverlapSize(25);
        props.setSplitStrategy(SplitStrategy.TOKEN);
        props.setAutoIndexOnStartup(false);

        assertFalse(props.isEnabled());
        assertEquals("custom-path", props.getPath());
        assertEquals("text-embedding-v2", props.getEmbeddingModel());
        assertEquals(768, props.getDimensions());
        assertEquals(256, props.getChunkSize());
        assertEquals(25, props.getOverlapSize());
        assertEquals(SplitStrategy.TOKEN, props.getSplitStrategy());
        assertFalse(props.isAutoIndexOnStartup());
    }

    @Test
    void enabled_canBeTrue() {
        KnowledgeProperties props = new KnowledgeProperties();
        props.setEnabled(true);

        assertTrue(props.isEnabled());
    }

    @Test
    void enabled_canBeFalse() {
        KnowledgeProperties props = new KnowledgeProperties();
        props.setEnabled(false);

        assertFalse(props.isEnabled());
    }

    @Test
    void splitStrategy_paragraph() {
        KnowledgeProperties props = new KnowledgeProperties();
        props.setSplitStrategy(SplitStrategy.PARAGRAPH);

        assertEquals(SplitStrategy.PARAGRAPH, props.getSplitStrategy());
    }

    @Test
    void splitStrategy_token() {
        KnowledgeProperties props = new KnowledgeProperties();
        props.setSplitStrategy(SplitStrategy.TOKEN);

        assertEquals(SplitStrategy.TOKEN, props.getSplitStrategy());
    }

    @Test
    void chunkSize_small() {
        KnowledgeProperties props = new KnowledgeProperties();
        props.setChunkSize(128);

        assertEquals(128, props.getChunkSize());
    }

    @Test
    void chunkSize_large() {
        KnowledgeProperties props = new KnowledgeProperties();
        props.setChunkSize(2048);

        assertEquals(2048, props.getChunkSize());
    }
}

package com.skloda.agentscope.service;

import com.skloda.agentscope.model.KnowledgeFileStatus;
import com.skloda.agentscope.model.KnowledgeIndexStatus;
import io.agentscope.core.rag.reader.SplitStrategy;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeServiceStatusTest {

    @TempDir
    Path tempDir;

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

    @Test
    void scanMarksEmptyDirectoryAsEmpty() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setPath(tempDir.resolve("missing-knowledge").toString());
        KnowledgeService service = new KnowledgeService("test-api-key", properties);

        service.indexLocalKnowledge();

        KnowledgeIndexStatus status = service.getIndexStatus();
        assertEquals(KnowledgeIndexStatus.State.EMPTY, status.getState());
        assertEquals(0, status.getTotalFiles());
        assertEquals(Path.of(properties.getPath()).toAbsolutePath().normalize().toString(), status.getKnowledgePath());
    }

    @Test
    void scanSkipsImageFilesWithoutFailingIndex() throws Exception {
        Path knowledgePath = tempDir.resolve("knowledge");
        Files.createDirectories(knowledgePath);
        Files.write(knowledgePath.resolve("diagram.png"), new byte[] {1, 2, 3});

        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setPath(knowledgePath.toString());
        KnowledgeService service = new KnowledgeService("test-api-key", properties);

        service.indexLocalKnowledge();

        KnowledgeIndexStatus status = service.getIndexStatus();
        assertEquals(KnowledgeIndexStatus.State.EMPTY, status.getState());
        assertEquals(1, status.getTotalFiles());
        assertEquals(1, status.getSkippedFiles());
        KnowledgeFileStatus fileStatus = status.getDocuments().get(0);
        assertEquals(KnowledgeFileStatus.Status.SKIPPED, fileStatus.getStatus());
        assertTrue(fileStatus.getMessage().contains("image indexing requires OCR"));
    }

    @Test
    void unsupportedUploadMarksAggregateReadyWithErrors() {
        KnowledgeService service = new KnowledgeService("test-api-key", new KnowledgeProperties());

        assertThrows(RuntimeException.class, () ->
                service.addDocument(tempDir.resolve("unsupported.csv").toString(), "unsupported.csv"));

        KnowledgeIndexStatus status = service.getIndexStatus();
        assertEquals(KnowledgeIndexStatus.State.READY_WITH_ERRORS, status.getState());
        assertEquals(1, status.getTotalFiles());
        assertEquals(1, status.getFailedFiles());
        KnowledgeFileStatus fileStatus = status.getDocuments().get(0);
        assertEquals("uploads/unsupported.csv", fileStatus.getRelativePath());
        assertEquals(KnowledgeFileStatus.Status.FAILED, fileStatus.getStatus());
    }

    @Test
    void uploadStatusDoesNotCollideWithLocalFileOfSameName() throws Exception {
        Path knowledgePath = tempDir.resolve("knowledge");
        Files.createDirectories(knowledgePath);
        Files.write(knowledgePath.resolve("same.csv"), "local".getBytes());

        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setPath(knowledgePath.toString());
        KnowledgeService service = new KnowledgeService("test-api-key", properties);

        assertThrows(RuntimeException.class, () ->
                service.addDocument(tempDir.resolve("same.csv").toString(), "same.csv"));
        service.indexLocalKnowledge();

        KnowledgeIndexStatus status = service.getIndexStatus();
        assertEquals(KnowledgeIndexStatus.State.READY_WITH_ERRORS, status.getState());
        assertEquals(2, status.getTotalFiles());
        assertEquals(1, status.getFailedFiles());
        assertEquals(1, status.getSkippedFiles());
        assertEquals("same.csv", status.getDocuments().get(0).getRelativePath());
        assertEquals("uploads/same.csv", status.getDocuments().get(1).getRelativePath());
    }

    @Test
    void emptyLocalScanPreservesPreviousUploadFailureStatus() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setPath(tempDir.resolve("empty-knowledge").toString());
        KnowledgeService service = new KnowledgeService("test-api-key", properties);

        assertThrows(RuntimeException.class, () ->
                service.addDocument(tempDir.resolve("failed.csv").toString(), "failed.csv"));
        service.indexLocalKnowledge();

        KnowledgeIndexStatus status = service.getIndexStatus();
        assertEquals(KnowledgeIndexStatus.State.READY_WITH_ERRORS, status.getState());
        assertEquals(1, status.getTotalFiles());
        assertEquals("uploads/failed.csv", status.getDocuments().get(0).getRelativePath());
    }
}

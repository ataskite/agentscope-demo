package com.skloda.agentscope.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeIndexStatusTest {

    @Test
    void builderComputesCountsFromDocuments() {
        KnowledgeFileStatus indexed = KnowledgeFileStatus.builder()
                .fileName("one.md")
                .status(KnowledgeFileStatus.Status.INDEXED)
                .chunkCount(3)
                .build();
        KnowledgeFileStatus skipped = KnowledgeFileStatus.builder()
                .fileName("two.txt")
                .status(KnowledgeFileStatus.Status.SKIPPED)
                .build();
        KnowledgeFileStatus failed = KnowledgeFileStatus.builder()
                .fileName("three.pdf")
                .status(KnowledgeFileStatus.Status.FAILED)
                .message("parse failed")
                .build();
        KnowledgeFileStatus pending = KnowledgeFileStatus.builder()
                .fileName("four.docx")
                .status(KnowledgeFileStatus.Status.PENDING)
                .build();

        KnowledgeIndexStatus status = KnowledgeIndexStatus.builder()
                .state(KnowledgeIndexStatus.State.READY_WITH_ERRORS)
                .knowledgePath("/tmp/knowledge")
                .documents(List.of(indexed, skipped, failed, pending))
                .build();

        assertEquals(4, status.getTotalFiles());
        assertEquals(1, status.getIndexedFiles());
        assertEquals(1, status.getSkippedFiles());
        assertEquals(1, status.getFailedFiles());
    }

    @Test
    void builderUsesEmptyImmutableListWhenDocumentsAreNull() {
        KnowledgeIndexStatus status = KnowledgeIndexStatus.builder()
                .state(KnowledgeIndexStatus.State.EMPTY)
                .documents(null)
                .build();

        assertEquals(List.of(), status.getDocuments());
        assertThrows(UnsupportedOperationException.class, () -> status.getDocuments().add(null));
    }

    @Test
    void builderDefensivelyCopiesDocuments() {
        KnowledgeFileStatus indexed = KnowledgeFileStatus.builder()
                .fileName("one.md")
                .status(KnowledgeFileStatus.Status.INDEXED)
                .build();
        List<KnowledgeFileStatus> documents = new ArrayList<>();
        documents.add(indexed);

        KnowledgeIndexStatus status = KnowledgeIndexStatus.builder()
                .documents(documents)
                .build();
        documents.clear();

        assertEquals(1, status.getDocuments().size());
        assertEquals(1, status.getTotalFiles());
        assertThrows(UnsupportedOperationException.class, () -> status.getDocuments().add(indexed));
    }

    @Test
    void toBuilderRecomputesCountsWhenDocumentsChange() {
        KnowledgeFileStatus indexed = KnowledgeFileStatus.builder()
                .fileName("one.md")
                .status(KnowledgeFileStatus.Status.INDEXED)
                .build();
        KnowledgeFileStatus failed = KnowledgeFileStatus.builder()
                .fileName("two.pdf")
                .status(KnowledgeFileStatus.Status.FAILED)
                .build();

        KnowledgeIndexStatus original = KnowledgeIndexStatus.builder()
                .documents(List.of(indexed))
                .build();
        KnowledgeIndexStatus updated = original.toBuilder()
                .documents(List.of(failed))
                .build();

        assertEquals(1, original.getTotalFiles());
        assertEquals(1, original.getIndexedFiles());
        assertEquals(0, original.getFailedFiles());
        assertEquals(1, updated.getTotalFiles());
        assertEquals(0, updated.getIndexedFiles());
        assertEquals(1, updated.getFailedFiles());
    }

    @Test
    void builderDoesNotExposeDerivedCountSetters() {
        Set<String> builderMethods = Arrays.stream(KnowledgeIndexStatus.builder().getClass().getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertFalse(builderMethods.contains("totalFiles"));
        assertFalse(builderMethods.contains("indexedFiles"));
        assertFalse(builderMethods.contains("skippedFiles"));
        assertFalse(builderMethods.contains("failedFiles"));
    }
}

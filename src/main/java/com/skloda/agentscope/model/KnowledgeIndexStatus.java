package com.skloda.agentscope.model;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

@Value
public class KnowledgeIndexStatus {

    public enum State {
        EMPTY,
        PENDING,
        INDEXING,
        READY,
        READY_WITH_ERRORS,
        FAILED
    }

    State state;
    String knowledgePath;
    int totalFiles;
    int indexedFiles;
    int skippedFiles;
    int failedFiles;
    OffsetDateTime startedAt;
    OffsetDateTime finishedAt;
    List<KnowledgeFileStatus> documents;

    @Builder(toBuilder = true)
    public KnowledgeIndexStatus(
            State state,
            String knowledgePath,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            List<KnowledgeFileStatus> documents
    ) {
        List<KnowledgeFileStatus> copiedDocuments = documents == null
                ? List.of()
                : List.copyOf(documents);

        this.state = state;
        this.knowledgePath = knowledgePath;
        this.totalFiles = copiedDocuments.size();
        this.indexedFiles = countByStatus(copiedDocuments, KnowledgeFileStatus.Status.INDEXED);
        this.skippedFiles = countByStatus(copiedDocuments, KnowledgeFileStatus.Status.SKIPPED);
        this.failedFiles = countByStatus(copiedDocuments, KnowledgeFileStatus.Status.FAILED);
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.documents = copiedDocuments;
    }

    private static int countByStatus(List<KnowledgeFileStatus> documents, KnowledgeFileStatus.Status status) {
        return (int) documents.stream()
                .filter(document -> document.getStatus() == status)
                .count();
    }
}

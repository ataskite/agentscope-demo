package com.skloda.agentscope.model;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder(toBuilder = true)
public class KnowledgeFileStatus {

    public enum Status {
        PENDING,
        INDEXING,
        INDEXED,
        SKIPPED,
        FAILED
    }

    String fileName;
    String relativePath;
    String absolutePath;
    String extension;
    Status status;
    int chunkCount;
    String message;
    OffsetDateTime updatedAt;
}

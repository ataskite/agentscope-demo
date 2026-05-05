package com.skloda.agentscope.model;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class WorkflowRunSnapshot {

    public enum Status {
        RUNNING,
        COMPLETED,
        FAILED
    }

    private final String runId;
    private final String agentId;
    private final String sessionId;
    private final String inputPreview;
    private final Status status;
    private final String error;
    private final OffsetDateTime startedAt;
    private final OffsetDateTime finishedAt;
    private final List<Map<String, Object>> events;
}

package com.skloda.agentscope.service;

import com.skloda.agentscope.model.WorkflowRunSnapshot;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class WorkflowRunService {

    private static final int DEFAULT_MAX_RUNS = 100;
    private static final int INPUT_PREVIEW_LIMIT = 500;

    private final int maxRuns;
    private final Map<String, MutableWorkflowRun> runs = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public WorkflowRunService() {
        this(DEFAULT_MAX_RUNS);
    }

    WorkflowRunService(int maxRuns) {
        this.maxRuns = maxRuns;
    }

    public String startRun(String agentId, String sessionId, String input) {
        String runId = UUID.randomUUID().toString();
        runs.put(runId, new MutableWorkflowRun(sequence.incrementAndGet(), runId, agentId, sessionId, preview(input)));
        trimOldRuns();
        return runId;
    }

    public void recordEvent(String runId, Map<String, Object> event) {
        MutableWorkflowRun run = runs.get(runId);
        if (run != null && event != null) {
            run.addEvent(event);
        }
    }

    public void completeRun(String runId) {
        MutableWorkflowRun run = runs.get(runId);
        if (run != null) {
            run.complete();
        }
    }

    public void failRun(String runId, Throwable error) {
        MutableWorkflowRun run = runs.get(runId);
        if (run != null) {
            run.fail(error != null && error.getMessage() != null ? error.getMessage() : "Unknown error");
        }
    }

    public Optional<WorkflowRunSnapshot> getRun(String runId) {
        MutableWorkflowRun run = runs.get(runId);
        return run != null ? Optional.of(run.snapshot()) : Optional.empty();
    }

    public List<WorkflowRunSnapshot> listRuns() {
        return runs.values().stream()
                .sorted(Comparator.comparingLong(MutableWorkflowRun::sequence).reversed())
                .map(MutableWorkflowRun::snapshot)
                .toList();
    }

    private String preview(String input) {
        if (input == null) {
            return "";
        }
        String normalized = input.strip();
        if (normalized.length() <= INPUT_PREVIEW_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, INPUT_PREVIEW_LIMIT);
    }

    private void trimOldRuns() {
        if (runs.size() <= maxRuns) {
            return;
        }
        List<MutableWorkflowRun> sorted = runs.values().stream()
                .sorted(Comparator.comparingLong(MutableWorkflowRun::sequence).reversed())
                .toList();
        for (int i = maxRuns; i < sorted.size(); i++) {
            runs.remove(sorted.get(i).runId);
        }
    }

    private static class MutableWorkflowRun {
        private final long sequence;
        private final String runId;
        private final String agentId;
        private final String sessionId;
        private final String inputPreview;
        private final OffsetDateTime startedAt;
        private final List<Map<String, Object>> events = new ArrayList<>();
        private WorkflowRunSnapshot.Status status = WorkflowRunSnapshot.Status.RUNNING;
        private OffsetDateTime finishedAt;
        private String error;

        private MutableWorkflowRun(long sequence, String runId, String agentId, String sessionId, String inputPreview) {
            this.sequence = sequence;
            this.runId = runId;
            this.agentId = agentId;
            this.sessionId = sessionId;
            this.inputPreview = inputPreview;
            this.startedAt = OffsetDateTime.now();
        }

        private long sequence() {
            return sequence;
        }

        private synchronized void addEvent(Map<String, Object> event) {
            events.add(new LinkedHashMap<>(event));
        }

        private synchronized void complete() {
            status = WorkflowRunSnapshot.Status.COMPLETED;
            finishedAt = OffsetDateTime.now();
        }

        private synchronized void fail(String message) {
            status = WorkflowRunSnapshot.Status.FAILED;
            error = message;
            finishedAt = OffsetDateTime.now();
        }

        private synchronized WorkflowRunSnapshot snapshot() {
            return WorkflowRunSnapshot.builder()
                    .runId(runId)
                    .agentId(agentId)
                    .sessionId(sessionId)
                    .inputPreview(inputPreview)
                    .status(status)
                    .error(error)
                    .startedAt(startedAt)
                    .finishedAt(finishedAt)
                    .events(List.copyOf(events))
                    .build();
        }
    }
}

package com.skloda.agentscope.service;

import com.skloda.agentscope.model.WorkflowRunSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowRunServiceTest {

    @Test
    void recordsEventsAndCompletesRunForReplay() {
        WorkflowRunService service = new WorkflowRunService();

        String runId = service.startRun("contract-review-workflow", "session-1", "审查合同");
        service.recordEvent(runId, Map.of("type", "text", "content", "分析中"));
        service.recordEvent(runId, Map.of("type", "structured_data", "data", "{\"summary\":\"ok\"}"));
        service.completeRun(runId);

        WorkflowRunSnapshot snapshot = service.getRun(runId).orElseThrow();

        assertEquals(runId, snapshot.getRunId());
        assertEquals("contract-review-workflow", snapshot.getAgentId());
        assertEquals(WorkflowRunSnapshot.Status.COMPLETED, snapshot.getStatus());
        assertEquals(2, snapshot.getEvents().size());
        assertTrue(service.listRuns().stream().anyMatch(run -> run.getRunId().equals(runId)));
    }

    @Test
    void keepsOnlyMostRecentRuns() {
        WorkflowRunService service = new WorkflowRunService(2);

        String first = service.startRun("a", null, "first");
        service.startRun("b", null, "second");
        service.startRun("c", null, "third");

        List<WorkflowRunSnapshot> runs = service.listRuns();

        assertEquals(2, runs.size());
        assertTrue(service.getRun(first).isEmpty());
        assertEquals(List.of("c", "b"), runs.stream().map(WorkflowRunSnapshot::getAgentId).toList());
    }
}

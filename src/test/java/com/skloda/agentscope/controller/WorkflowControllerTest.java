package com.skloda.agentscope.controller;

import com.skloda.agentscope.model.WorkflowRunSnapshot;
import com.skloda.agentscope.service.WorkflowRunService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowControllerTest {

    @Test
    void listRunsReturnsMostRecentWorkflowSnapshots() {
        WorkflowRunService service = new WorkflowRunService();
        String runId = service.startRun("contract-review-workflow", null, "审查合同");
        service.completeRun(runId);
        WorkflowController controller = new WorkflowController(service);

        List<WorkflowRunSnapshot> runs = controller.listRuns();

        assertEquals(1, runs.size());
        assertEquals("contract-review-workflow", runs.get(0).getAgentId());
    }

    @Test
    void getRunReturnsNotFoundForUnknownRunId() {
        WorkflowController controller = new WorkflowController(new WorkflowRunService());

        ResponseEntity<WorkflowRunSnapshot> response = controller.getRun("missing");

        assertEquals(404, response.getStatusCode().value());
    }
}

package com.skloda.agentscope.controller;

import com.skloda.agentscope.model.WorkflowRunSnapshot;
import com.skloda.agentscope.service.WorkflowRunService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowRunService workflowRunService;

    public WorkflowController(WorkflowRunService workflowRunService) {
        this.workflowRunService = workflowRunService;
    }

    @GetMapping
    public List<WorkflowRunSnapshot> listRuns() {
        return workflowRunService.listRuns();
    }

    @GetMapping("/{runId}")
    public ResponseEntity<WorkflowRunSnapshot> getRun(@PathVariable String runId) {
        return workflowRunService.getRun(runId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

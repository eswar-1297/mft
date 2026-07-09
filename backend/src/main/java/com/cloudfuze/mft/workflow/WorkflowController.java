package com.cloudfuze.mft.workflow;

import com.cloudfuze.mft.common.ApiException;
import com.cloudfuze.mft.workflow.dto.WorkflowRequest;
import com.cloudfuze.mft.workflow.dto.WorkflowRunView;
import com.cloudfuze.mft.workflow.dto.WorkflowView;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowRunRepository runs;

    public WorkflowController(WorkflowService workflowService, WorkflowRunRepository runs) {
        this.workflowService = workflowService;
        this.runs = runs;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','AUDITOR')")
    public List<WorkflowView> list() {
        return workflowService.list().stream().map(WorkflowView::of).toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<WorkflowView> create(@Valid @RequestBody WorkflowRequest req) {
        WorkflowDef def = workflowService.create(req.name(), req.steps());
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkflowView.of(def));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','AUDITOR')")
    public WorkflowView get(@PathVariable UUID id) {
        return WorkflowView.of(workflowService.get(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        workflowService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Start a durable run of this pipeline. Returns 202 with the run in RUNNING; poll the run. */
    @PostMapping("/{id}/run")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<WorkflowRunView> run(@PathVariable UUID id) {
        WorkflowRun run = workflowService.run(id);
        return ResponseEntity.accepted().body(WorkflowRunView.of(run));
    }

    public record ScheduleRequest(String cron) {
    }

    /** Attach a cron schedule so the workflow fires automatically. */
    @PostMapping("/{id}/schedule")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public WorkflowView schedule(@PathVariable UUID id, @RequestBody ScheduleRequest req) {
        return WorkflowView.of(workflowService.schedule(id, req.cron()));
    }

    /** Remove a workflow's schedule. */
    @DeleteMapping("/{id}/schedule")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public WorkflowView unschedule(@PathVariable UUID id) {
        return WorkflowView.of(workflowService.unschedule(id));
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','AUDITOR')")
    public Page<WorkflowRunView> runs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return runs.findAllByOrderByStartedAtDesc(PageRequest.of(page, Math.min(size, 200)))
                .map(WorkflowRunView::of);
    }

    @GetMapping("/runs/{runId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','AUDITOR')")
    public WorkflowRunView getRun(@PathVariable UUID runId) {
        return runs.findScopedById(runId)
                .map(WorkflowRunView::of)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Run not found"));
    }
}

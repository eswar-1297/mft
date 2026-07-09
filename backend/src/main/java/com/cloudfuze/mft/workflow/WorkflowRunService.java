package com.cloudfuze.mft.workflow;

import com.cloudfuze.mft.audit.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DB + audit lifecycle for a workflow run (each method its own short transaction, called from the
 * Temporal activities). Keeps the durable pipeline's persistence separate from its I/O.
 */
@Service
public class WorkflowRunService {

    private final WorkflowDefRepository defs;
    private final WorkflowRunRepository runs;
    private final AuditService audit;
    private final ObjectMapper json = new ObjectMapper();

    public WorkflowRunService(WorkflowDefRepository defs, WorkflowRunRepository runs, AuditService audit) {
        this.defs = defs;
        this.runs = runs;
        this.audit = audit;
    }

    @Transactional
    public void markRunStarted(UUID runId) {
        audit.record("workflow.run.started", "workflow_run", runId.toString(), Map.of());
    }

    /** Create a new run row for a scheduled fire; returns the run id and the def's current steps. */
    @Transactional
    public com.cloudfuze.mft.workflow.temporal.ScheduledRunStart beginScheduledRun(UUID workflowId) {
        WorkflowDef def = defs.findById(workflowId)
                .orElseThrow(() -> new IllegalStateException("Scheduled workflow no longer exists"));
        WorkflowRun run = new WorkflowRun(UUID.randomUUID(), def.getId(), def.getName());
        runs.save(run);
        audit.record("workflow.run.created", "workflow_run", run.getId().toString(),
                Map.of("workflow", def.getName(), "trigger", "schedule"));
        return new com.cloudfuze.mft.workflow.temporal.ScheduledRunStart(
                run.getId().toString(), WorkflowSteps.fromJson(def.getStepsJson()));
    }

    @Transactional
    public void recordStepResult(UUID runId, int index, StepType type, String status, String detail) {
        audit.record("workflow.step." + status.toLowerCase(), "workflow_run", runId.toString(),
                Map.of("step", index, "type", type.name(), "detail", detail));
    }

    @Transactional
    public void completeRun(UUID runId, UUID workflowId, boolean success,
                            List<Map<String, Object>> stepResults, String error) {
        WorkflowRun run = runs.findById(runId).orElseThrow();
        String resultsJson = writeJson(stepResults);
        if (success) {
            run.succeed(resultsJson);
        } else {
            run.fail(resultsJson, error);
        }
        runs.save(run);
        defs.findById(workflowId).ifPresent(d -> {
            d.recordRun(success ? "SUCCEEDED" : "FAILED");
            defs.save(d);
        });
        audit.record(success ? "workflow.run.succeeded" : "workflow.run.failed",
                "workflow_run", runId.toString(),
                error == null ? Map.of("steps", stepResults.size())
                        : Map.of("steps", stepResults.size(), "error", error));
    }

    private String writeJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }
}

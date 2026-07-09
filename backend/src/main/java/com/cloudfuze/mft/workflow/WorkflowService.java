package com.cloudfuze.mft.workflow;

import com.cloudfuze.mft.audit.AuditService;
import com.cloudfuze.mft.common.ApiException;
import com.cloudfuze.mft.config.TemporalProperties;
import com.cloudfuze.mft.tenant.TenantContext;
import com.cloudfuze.mft.workflow.temporal.PipelineJob;
import com.cloudfuze.mft.workflow.temporal.PipelineWorkflow;
import com.cloudfuze.mft.workflow.temporal.ScheduledPipelineJob;
import com.cloudfuze.mft.workflow.temporal.ScheduledPipelineWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkflowService {

    private final WorkflowDefRepository defs;
    private final WorkflowRunRepository runs;
    private final WorkflowClient workflowClient;
    private final TemporalProperties props;
    private final AuditService audit;

    public WorkflowService(WorkflowDefRepository defs, WorkflowRunRepository runs,
                           WorkflowClient workflowClient, TemporalProperties props,
                           AuditService audit) {
        this.defs = defs;
        this.runs = runs;
        this.workflowClient = workflowClient;
        this.props = props;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<WorkflowDef> list() {
        return defs.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public WorkflowDef get(UUID id) {
        return defs.findScopedById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Workflow not found"));
    }

    @Transactional
    public WorkflowDef create(String name, List<WorkflowStep> steps) {
        if (defs.existsByName(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "A workflow with that name already exists");
        }
        if (steps == null || steps.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A workflow needs at least one step");
        }
        WorkflowDef def = new WorkflowDef(UUID.randomUUID(), name, WorkflowSteps.toJson(steps));
        defs.save(def);
        audit.record("workflow.created", "workflow", def.getId().toString(),
                Map.of("name", name, "steps", steps.size()));
        return def;
    }

    @Transactional
    public void delete(UUID id) {
        WorkflowDef def = get(id);
        defs.delete(def);
        audit.record("workflow.deleted", "workflow", id.toString(), Map.of("name", def.getName()));
    }

    /**
     * Create a run record and start a durable pipeline workflow. Returns the run id immediately;
     * the caller polls the run for per-step progress.
     */
    @Transactional
    public WorkflowRun run(UUID workflowId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant bound");
        }
        WorkflowDef def = get(workflowId);
        List<WorkflowStep> steps = WorkflowSteps.fromJson(def.getStepsJson());

        WorkflowRun run = new WorkflowRun(UUID.randomUUID(), def.getId(), def.getName());
        runs.save(run);
        audit.record("workflow.run.created", "workflow_run", run.getId().toString(),
                Map.of("workflow", def.getName()));

        PipelineJob job = new PipelineJob(tenantId, def.getId().toString(), run.getId().toString(),
                def.getName(), steps);
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(props.getTaskQueue())
                .setWorkflowId("pipeline-" + run.getId())
                .build();
        PipelineWorkflow workflow = workflowClient.newWorkflowStub(PipelineWorkflow.class, options);
        WorkflowClient.start(workflow::run, job);
        return run;
    }

    /** Stable Temporal workflow id for a definition's schedule (one cron workflow per definition). */
    private String scheduleWorkflowId(UUID workflowId) {
        return "schedule-" + workflowId;
    }

    /**
     * Attach (or replace) a cron schedule on a workflow. Starts a Temporal cron workflow that fires
     * the pipeline on the given cadence. Idempotent — re-scheduling cancels the prior schedule first.
     */
    @Transactional
    public WorkflowDef schedule(UUID workflowId, String cron) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant bound");
        }
        if (cron == null || cron.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A cron expression is required");
        }
        WorkflowDef def = get(workflowId);

        cancelSchedule(workflowId); // ensure no duplicate cron workflow

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(props.getTaskQueue())
                .setWorkflowId(scheduleWorkflowId(workflowId))
                .setCronSchedule(cron)
                .build();
        ScheduledPipelineWorkflow wf = workflowClient.newWorkflowStub(ScheduledPipelineWorkflow.class, options);
        WorkflowClient.start(wf::run, new ScheduledPipelineJob(tenantId, workflowId.toString()));

        def.setCronSchedule(cron);
        defs.save(def);
        audit.record("workflow.scheduled", "workflow", workflowId.toString(),
                Map.of("cron", cron, "name", def.getName()));
        return def;
    }

    /** Remove a workflow's schedule (terminates the cron workflow if running). */
    @Transactional
    public WorkflowDef unschedule(UUID workflowId) {
        WorkflowDef def = get(workflowId);
        cancelSchedule(workflowId);
        def.setCronSchedule(null);
        defs.save(def);
        audit.record("workflow.unscheduled", "workflow", workflowId.toString(),
                Map.of("name", def.getName()));
        return def;
    }

    private void cancelSchedule(UUID workflowId) {
        try {
            WorkflowStub stub = workflowClient.newUntypedWorkflowStub(scheduleWorkflowId(workflowId));
            stub.terminate("rescheduled-or-unscheduled");
        } catch (Exception ignored) {
            // no existing cron workflow to cancel — fine
        }
    }
}

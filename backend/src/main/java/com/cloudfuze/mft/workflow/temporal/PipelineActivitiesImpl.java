package com.cloudfuze.mft.workflow.temporal;

import com.cloudfuze.mft.tenant.TenantContext;
import com.cloudfuze.mft.workflow.Artifact;
import com.cloudfuze.mft.workflow.StepExecutor;
import com.cloudfuze.mft.workflow.StepType;
import com.cloudfuze.mft.workflow.WorkflowRunService;
import com.cloudfuze.mft.workflow.WorkflowStep;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Executes pipeline activities, binding the tenant from the job on each call. */
@Component
public class PipelineActivitiesImpl implements PipelineActivities {

    private final StepExecutor stepExecutor;
    private final WorkflowRunService runService;

    public PipelineActivitiesImpl(StepExecutor stepExecutor, WorkflowRunService runService) {
        this.stepExecutor = stepExecutor;
        this.runService = runService;
    }

    @Override
    public void runStarted(String tenantId, String runId) {
        inTenant(tenantId, () -> {
            runService.markRunStarted(UUID.fromString(runId));
            return null;
        });
    }

    @Override
    public ScheduledRunStart beginScheduledRun(String tenantId, String workflowId) {
        return inTenant(tenantId, () -> runService.beginScheduledRun(UUID.fromString(workflowId)));
    }

    @Override
    public StepOutcome executeStep(String tenantId, String runId, int index, WorkflowStep step,
                                   Artifact current) {
        return inTenant(tenantId, () -> {
            Artifact next = stepExecutor.execute(step, current);
            String detail = describe(step.type(), next);
            runService.recordStepResult(UUID.fromString(runId), index, step.type(), "succeeded", detail);
            return new StepOutcome(next, detail);
        });
    }

    @Override
    public void completeRun(String tenantId, String runId, String workflowId, boolean success,
                            List<Map<String, Object>> stepResults, String error) {
        inTenant(tenantId, () -> {
            runService.completeRun(UUID.fromString(runId), UUID.fromString(workflowId),
                    success, stepResults, error);
            return null;
        });
    }

    private String describe(StepType type, Artifact a) {
        return switch (type) {
            case PICKUP -> "picked up " + a.filename() + " (" + a.bytes() + " bytes)";
            case PGP_ENCRYPT -> "encrypted → " + a.filename();
            case PGP_DECRYPT -> "decrypted → " + a.filename();
            case VALIDATE -> "validated " + a.bytes() + " bytes";
            case SEND -> "sent " + a.filename() + " (" + a.bytes() + " bytes)";
            case ARCHIVE -> "archived to " + a.key();
            case NOTIFY -> "notification recorded";
        };
    }

    private <T> T inTenant(String tenantId, Supplier<T> action) {
        TenantContext.set(tenantId);
        try {
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }
}

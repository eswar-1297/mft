package com.cloudfuze.mft.workflow.temporal;

import com.cloudfuze.mft.workflow.Artifact;
import com.cloudfuze.mft.workflow.WorkflowStep;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One cron fire = one pipeline run. Temporal invokes {@link #run} on the schedule; this creates a
 * fresh run record (re-reading the definition's current steps so edits take effect), executes the
 * steps as durable activities, and completes the run — mirroring {@link PipelineWorkflowImpl} but
 * sourcing a new run id per fire. The workflow stays scheduled until terminated.
 */
public class ScheduledPipelineWorkflowImpl implements ScheduledPipelineWorkflow {

    private static final Logger log = Workflow.getLogger(ScheduledPipelineWorkflowImpl.class);

    private final PipelineActivities activities = Workflow.newActivityStub(
            PipelineActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(30))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
                    .build());

    @Override
    public void run(ScheduledPipelineJob job) {
        ScheduledRunStart start = activities.beginScheduledRun(job.tenantId(), job.workflowId());
        String runId = start.runId();
        activities.runStarted(job.tenantId(), runId);

        List<WorkflowStep> steps = start.steps();
        List<Map<String, Object>> results = new ArrayList<>();
        Artifact current = Artifact.none();

        for (int i = 0; i < steps.size(); i++) {
            WorkflowStep step = steps.get(i);
            try {
                StepOutcome outcome = activities.executeStep(job.tenantId(), runId, i, step, current);
                current = outcome.artifact();
                results.add(stepResult(i, step, "SUCCEEDED", outcome.detail()));
            } catch (Exception e) {
                String msg = rootMessage(e);
                log.warn("Scheduled run {} failed at step {} ({}): {}", runId, i, step.type(), msg);
                results.add(stepResult(i, step, "FAILED", msg));
                activities.completeRun(job.tenantId(), runId, job.workflowId(), false, results, msg);
                return;
            }
        }
        activities.completeRun(job.tenantId(), runId, job.workflowId(), true, results, null);
    }

    private static Map<String, Object> stepResult(int index, WorkflowStep step, String status,
                                                  String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("index", index);
        m.put("type", step.type().name());
        m.put("status", status);
        m.put("detail", detail);
        return m;
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String m = cur.getMessage();
        return (m == null || m.isBlank()) ? cur.getClass().getSimpleName() : m;
    }
}

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
 * Runs a pipeline's steps in order as durable activities. Each step is retried a few times for
 * transient failures; if a step ultimately fails, the run is marked FAILED at the step that broke
 * (so the UI shows exactly where), and remaining steps are skipped. The whole pipeline survives
 * worker restarts because Temporal persists and replays the workflow state.
 */
public class PipelineWorkflowImpl implements PipelineWorkflow {

    private static final Logger log = Workflow.getLogger(PipelineWorkflowImpl.class);

    private final PipelineActivities activities = Workflow.newActivityStub(
            PipelineActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(30))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
                    .build());

    @Override
    public void run(PipelineJob job) {
        activities.runStarted(job.tenantId(), job.runId());

        List<Map<String, Object>> results = new ArrayList<>();
        Artifact current = Artifact.none();
        List<WorkflowStep> steps = job.steps();

        for (int i = 0; i < steps.size(); i++) {
            WorkflowStep step = steps.get(i);
            try {
                StepOutcome outcome = activities.executeStep(
                        job.tenantId(), job.runId(), i, step, current);
                current = outcome.artifact();
                results.add(stepResult(i, step, "SUCCEEDED", outcome.detail()));
            } catch (Exception e) {
                String msg = rootMessage(e);
                log.warn("Pipeline {} failed at step {} ({}): {}", job.runId(), i, step.type(), msg);
                results.add(stepResult(i, step, "FAILED", msg));
                activities.completeRun(job.tenantId(), job.runId(), job.workflowId(), false, results, msg);
                return;
            }
        }
        activities.completeRun(job.tenantId(), job.runId(), job.workflowId(), true, results, null);
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

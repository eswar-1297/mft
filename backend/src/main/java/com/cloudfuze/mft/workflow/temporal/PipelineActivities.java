package com.cloudfuze.mft.workflow.temporal;

import com.cloudfuze.mft.workflow.Artifact;
import com.cloudfuze.mft.workflow.WorkflowStep;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;
import java.util.Map;

/** The side-effecting steps a pipeline workflow drives. */
@ActivityInterface
public interface PipelineActivities {

    @ActivityMethod
    void runStarted(String tenantId, String runId);

    /** Create a fresh run for a scheduled fire and return its id + the workflow's current steps. */
    @ActivityMethod
    ScheduledRunStart beginScheduledRun(String tenantId, String workflowId);

    /** Execute one step against the incoming artifact and return the next artifact + a detail line. */
    @ActivityMethod
    StepOutcome executeStep(String tenantId, String runId, int index, WorkflowStep step, Artifact current);

    @ActivityMethod
    void completeRun(String tenantId, String runId, String workflowId, boolean success,
                     List<Map<String, Object>> stepResults, String error);
}

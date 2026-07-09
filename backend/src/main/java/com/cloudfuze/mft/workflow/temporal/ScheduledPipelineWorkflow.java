package com.cloudfuze.mft.workflow.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * A workflow started with a Temporal cron schedule. Temporal re-executes it on the cadence; each
 * execution creates and runs one pipeline run. Stopping is done by terminating this workflow.
 */
@WorkflowInterface
public interface ScheduledPipelineWorkflow {

    @WorkflowMethod
    void run(ScheduledPipelineJob job);
}

package com.cloudfuze.mft.workflow.temporal;

import com.cloudfuze.mft.workflow.WorkflowStep;

import java.util.List;

/** Immutable input to a pipeline workflow. Carries the tenant so worker activities can rebind it. */
public record PipelineJob(
        String tenantId,
        String workflowId,
        String runId,
        String workflowName,
        List<WorkflowStep> steps) {
}

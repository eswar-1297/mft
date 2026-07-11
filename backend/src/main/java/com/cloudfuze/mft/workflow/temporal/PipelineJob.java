package com.cloudfuze.mft.workflow.temporal;

import com.cloudfuze.mft.workflow.Artifact;
import com.cloudfuze.mft.workflow.WorkflowStep;

import java.util.List;

/**
 * Immutable input to a pipeline workflow. Carries the tenant so worker activities can rebind it.
 *
 * <p>{@code seedArtifact} is set only for system-triggered runs that already have a file in hand
 * (e.g. an AS2 receive) — the pipeline starts from this artifact instead of {@code Artifact.none()},
 * skipping the need for a leading PICKUP step. Null for manual/scheduled runs.
 */
public record PipelineJob(
        String tenantId,
        String workflowId,
        String runId,
        String workflowName,
        List<WorkflowStep> steps,
        Artifact seedArtifact) {
}

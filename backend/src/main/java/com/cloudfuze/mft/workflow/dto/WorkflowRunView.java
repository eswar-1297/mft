package com.cloudfuze.mft.workflow.dto;

import com.cloudfuze.mft.workflow.WorkflowRun;

public record WorkflowRunView(
        String id,
        String workflowId,
        String workflowName,
        String status,
        String stepResultsJson,
        String errorMessage,
        String startedAt,
        String completedAt) {

    public static WorkflowRunView of(WorkflowRun r) {
        return new WorkflowRunView(
                r.getId().toString(),
                r.getWorkflowId().toString(),
                r.getWorkflowName(),
                r.getStatus(),
                r.getStepResultsJson(),
                r.getErrorMessage(),
                r.getStartedAt().toString(),
                r.getCompletedAt() != null ? r.getCompletedAt().toString() : null);
    }
}

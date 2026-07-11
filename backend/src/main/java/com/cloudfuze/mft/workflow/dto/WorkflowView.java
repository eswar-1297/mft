package com.cloudfuze.mft.workflow.dto;

import com.cloudfuze.mft.workflow.WorkflowDef;
import com.cloudfuze.mft.workflow.WorkflowStep;
import com.cloudfuze.mft.workflow.WorkflowSteps;

import java.util.List;

public record WorkflowView(
        String id,
        String name,
        List<WorkflowStep> steps,
        boolean enabled,
        String createdAt,
        String lastRunAt,
        String lastStatus,
        String cronSchedule,
        String as2TriggerPartnerId) {

    public static WorkflowView of(WorkflowDef d) {
        return new WorkflowView(
                d.getId().toString(),
                d.getName(),
                WorkflowSteps.fromJson(d.getStepsJson()),
                d.isEnabled(),
                d.getCreatedAt().toString(),
                d.getLastRunAt() != null ? d.getLastRunAt().toString() : null,
                d.getLastStatus(),
                d.getCronSchedule(),
                d.getAs2TriggerPartnerId() != null ? d.getAs2TriggerPartnerId().toString() : null);
    }
}

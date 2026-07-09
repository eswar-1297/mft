package com.cloudfuze.mft.workflow.dto;

import com.cloudfuze.mft.workflow.WorkflowStep;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record WorkflowRequest(
        @NotBlank String name,
        @NotEmpty List<WorkflowStep> steps) {
}

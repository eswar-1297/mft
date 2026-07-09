package com.cloudfuze.mft.copilot.dto;

import com.cloudfuze.mft.workflow.WorkflowStep;

import java.util.List;

/**
 * A PROPOSED workflow the Copilot drafted from natural language. It is NOT created — the user
 * reviews it and saves it through the normal workflow API. This keeps the Copilot advisory, never
 * autonomous, on a security-critical system.
 */
public record DraftWorkflowResponse(
        String name,
        List<WorkflowStep> steps,
        String suggestedCron,
        List<String> notes,
        boolean aiAssisted) {
}

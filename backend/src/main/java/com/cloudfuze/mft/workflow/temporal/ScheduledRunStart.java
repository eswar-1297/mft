package com.cloudfuze.mft.workflow.temporal;

import com.cloudfuze.mft.workflow.WorkflowStep;

import java.util.List;

/**
 * What a cron fire needs to execute one scheduled run: the freshly-created run id and the
 * workflow's current step list (re-read each fire, so edits to the definition take effect).
 */
public record ScheduledRunStart(String runId, List<WorkflowStep> steps) {
}

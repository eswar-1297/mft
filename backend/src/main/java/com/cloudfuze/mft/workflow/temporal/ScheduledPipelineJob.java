package com.cloudfuze.mft.workflow.temporal;

/** Input to a scheduled (cron) pipeline: which tenant's workflow definition to run on each fire. */
public record ScheduledPipelineJob(String tenantId, String workflowId) {
}

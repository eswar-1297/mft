package com.cloudfuze.mft.transfer.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** The durable orchestration for a single file transfer. */
@WorkflowInterface
public interface TransferWorkflow {

    /**
     * Run the transfer to completion. Durable: if the worker crashes mid-flight, Temporal resumes
     * this workflow from where it left off on another worker — the job is never silently lost.
     */
    @WorkflowMethod
    void run(TransferJob job);
}

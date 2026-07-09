package com.cloudfuze.mft.workflow.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** Durable orchestration of a multi-step transfer pipeline. */
@WorkflowInterface
public interface PipelineWorkflow {

    @WorkflowMethod
    void run(PipelineJob job);
}

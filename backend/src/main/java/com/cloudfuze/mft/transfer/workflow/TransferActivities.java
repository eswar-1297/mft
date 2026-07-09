package com.cloudfuze.mft.transfer.workflow;

import com.cloudfuze.mft.transfer.TransferResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * The side-effecting steps a transfer workflow drives. Each runs on a worker thread and re-binds
 * the tenant from the job. Activities are the only place real I/O and DB writes happen; the
 * workflow itself stays deterministic.
 */
@ActivityInterface
public interface TransferActivities {

    @ActivityMethod
    void markRunning(String tenantId, String transferId);

    /** Perform one physical transfer attempt. Throws on failure (Temporal surfaces it to the workflow). */
    @ActivityMethod
    TransferResult execute(TransferJob job);

    @ActivityMethod
    void markSucceeded(String tenantId, String transferId, TransferResult result);

    @ActivityMethod
    void markFailed(String tenantId, String transferId, String error);

    @ActivityMethod
    void recordRetry(String tenantId, String transferId, int attempt, String reason);
}

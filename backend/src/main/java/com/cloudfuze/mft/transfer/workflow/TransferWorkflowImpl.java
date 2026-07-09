package com.cloudfuze.mft.transfer.workflow;

import com.cloudfuze.mft.transfer.TransferResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

/**
 * Durable transfer orchestration. The workflow itself owns retry with exponential backoff so the
 * behaviour (attempt count, backoff, audit events) is explicit and deterministic. Each retry — and
 * the whole workflow — survives worker restarts because Temporal persists the workflow's state and
 * replays it. This is the real engine behind the "auto-retry, resume, MTTR < 15 min" story.
 */
public class TransferWorkflowImpl implements TransferWorkflow {

    private static final int MAX_ATTEMPTS = 3;
    private static final Logger log = Workflow.getLogger(TransferWorkflowImpl.class);

    // The physical transfer: long timeout, and NO Temporal-level retry — this workflow controls retries.
    private final TransferActivities io = Workflow.newActivityStub(
            TransferActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(30))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                    .build());

    // Quick DB/audit steps: short timeout, a few automatic retries for transient blips.
    private final TransferActivities ops = Workflow.newActivityStub(
            TransferActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(5).build())
                    .build());

    @Override
    public void run(TransferJob job) {
        String tenantId = job.tenantId();
        String transferId = job.transferId();
        String lastError = "unknown error";

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            ops.markRunning(tenantId, transferId);
            try {
                TransferResult result = io.execute(job);
                ops.markSucceeded(tenantId, transferId, result);
                return;
            } catch (Exception e) {
                lastError = rootMessage(e);
                log.warn("Transfer {} attempt {}/{} failed: {}", transferId, attempt, MAX_ATTEMPTS, lastError);
                if (attempt < MAX_ATTEMPTS) {
                    ops.recordRetry(tenantId, transferId, attempt, lastError);
                    // Durable backoff: 2s, 4s, ... survives worker restarts.
                    Workflow.sleep(Duration.ofSeconds(1L << attempt));
                }
            }
        }

        ops.markFailed(tenantId, transferId, lastError);
        // Fail the workflow so it is visibly failed in Temporal too (the transfer row is already FAILED).
        throw ApplicationFailure.newNonRetryableFailure(
                "Transfer failed after " + MAX_ATTEMPTS + " attempts: " + lastError, "TransferFailed");
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String m = cur.getMessage();
        return (m == null || m.isBlank()) ? cur.getClass().getSimpleName() : m;
    }
}

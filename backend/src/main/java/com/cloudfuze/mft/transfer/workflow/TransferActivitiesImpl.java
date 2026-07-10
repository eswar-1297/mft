package com.cloudfuze.mft.transfer.workflow;

import com.cloudfuze.mft.as2.As2Partner;
import com.cloudfuze.mft.as2.As2PartnerService;
import com.cloudfuze.mft.connector.SftpConnectionDetails;
import com.cloudfuze.mft.tenant.TenantContext;
import com.cloudfuze.mft.transfer.TransferResult;
import com.cloudfuze.mft.transfer.TransferService;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Executes transfer activities. Every method binds the tenant from its argument before touching
 * tenant-scoped storage or DB rows, and always clears it afterwards so worker threads never leak
 * tenant state between jobs.
 */
@Component
public class TransferActivitiesImpl implements TransferActivities {

    private final TransferService transferService;
    private final As2PartnerService as2Partners;

    public TransferActivitiesImpl(TransferService transferService, As2PartnerService as2Partners) {
        this.transferService = transferService;
        this.as2Partners = as2Partners;
    }

    @Override
    public void markRunning(String tenantId, String transferId) {
        inTenant(tenantId, () -> transferService.markRunning(UUID.fromString(transferId)));
    }

    @Override
    public TransferResult execute(TransferJob job) {
        TenantContext.set(job.tenantId());
        try {
            SftpConnectionDetails details = new SftpConnectionDetails(
                    job.sftpHost(), job.sftpPort(), job.sftpUsername(), job.sftpPassword(),
                    job.expectedHostKey());
            return switch (job.direction()) {
                case SFTP_PULL -> transferService.performPull(details, job.remotePath());
                case SFTP_PUSH -> transferService.performPush(job.sourceRef(), details, job.remotePath());
                case AS2_SEND -> {
                    As2Partner partner = as2Partners.get(UUID.fromString(job.as2PartnerId()));
                    yield transferService.performAs2Send(job.sourceRef(), partner);
                }
                case AS2_RECEIVE -> throw new IllegalStateException(
                        "AS2_RECEIVE is recorded directly by the inbound endpoint, never started as a job");
            };
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    public void markSucceeded(String tenantId, String transferId, TransferResult result) {
        inTenant(tenantId, () -> transferService.markSucceeded(UUID.fromString(transferId), result));
    }

    @Override
    public void markFailed(String tenantId, String transferId, String error) {
        inTenant(tenantId, () -> transferService.markFailed(UUID.fromString(transferId), error));
    }

    @Override
    public void recordRetry(String tenantId, String transferId, int attempt, String reason) {
        inTenant(tenantId, () ->
                transferService.recordRetryScheduled(UUID.fromString(transferId), attempt, reason));
    }

    private void inTenant(String tenantId, Runnable action) {
        TenantContext.set(tenantId);
        try {
            action.run();
        } finally {
            TenantContext.clear();
        }
    }
}

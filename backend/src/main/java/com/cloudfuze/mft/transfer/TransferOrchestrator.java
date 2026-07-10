package com.cloudfuze.mft.transfer;

import com.cloudfuze.mft.as2.As2Partner;
import com.cloudfuze.mft.config.TemporalProperties;
import com.cloudfuze.mft.connector.FtpsConnectionDetails;
import com.cloudfuze.mft.connector.SftpConnectionDetails;
import com.cloudfuze.mft.tenant.TenantContext;
import com.cloudfuze.mft.transfer.workflow.TransferJob;
import com.cloudfuze.mft.transfer.workflow.TransferWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.stereotype.Service;

/**
 * Creates the transfer record in the request thread (so it is attributed to the caller and bound
 * to the tenant), then hands execution to a durable Temporal workflow and returns immediately. The
 * caller polls {@code GET /api/transfers/{id}} for progress. Because execution is durable, the
 * transfer completes even if this API process restarts.
 */
@Service
public class TransferOrchestrator {

    private final WorkflowClient workflowClient;
    private final TransferService transferService;
    private final TemporalProperties props;

    public TransferOrchestrator(WorkflowClient workflowClient, TransferService transferService,
                                TemporalProperties props) {
        this.workflowClient = workflowClient;
        this.transferService = transferService;
        this.props = props;
    }

    public Transfer startPull(SftpConnectionDetails details, String remotePath) {
        String tenantId = requireTenant();
        Transfer t = transferService.createTransfer(
                TransferDirection.SFTP_PULL, remotePath, TransferService.basename(remotePath));
        TransferJob job = new TransferJob(tenantId, t.getId().toString(), TransferDirection.SFTP_PULL,
                remotePath, remotePath, details.host(), details.portOrDefault(),
                details.username(), details.password(), details.expectedHostKey(), null);
        startWorkflow(t, job);
        return t;
    }

    public Transfer startPush(String storageKey, SftpConnectionDetails details, String remotePath) {
        String tenantId = requireTenant();
        Transfer t = transferService.createTransfer(
                TransferDirection.SFTP_PUSH, storageKey, TransferService.basename(remotePath));
        TransferJob job = new TransferJob(tenantId, t.getId().toString(), TransferDirection.SFTP_PUSH,
                storageKey, remotePath, details.host(), details.portOrDefault(),
                details.username(), details.password(), details.expectedHostKey(), null);
        startWorkflow(t, job);
        return t;
    }

    public Transfer startFtpsPull(FtpsConnectionDetails details, String remotePath) {
        String tenantId = requireTenant();
        Transfer t = transferService.createTransfer(
                TransferDirection.FTPS_PULL, remotePath, TransferService.basename(remotePath));
        TransferJob job = new TransferJob(tenantId, t.getId().toString(), TransferDirection.FTPS_PULL,
                remotePath, remotePath, details.host(), details.portOrDefault(),
                details.username(), details.password(), null, null);
        startWorkflow(t, job);
        return t;
    }

    public Transfer startFtpsPush(String storageKey, FtpsConnectionDetails details, String remotePath) {
        String tenantId = requireTenant();
        Transfer t = transferService.createTransfer(
                TransferDirection.FTPS_PUSH, storageKey, TransferService.basename(remotePath));
        TransferJob job = new TransferJob(tenantId, t.getId().toString(), TransferDirection.FTPS_PUSH,
                storageKey, remotePath, details.host(), details.portOrDefault(),
                details.username(), details.password(), null, null);
        startWorkflow(t, job);
        return t;
    }

    /** Sign, encrypt, and send a stored object to an AS2 partner. Starts a durable workflow. */
    public Transfer startAs2Send(String storageKey, As2Partner partner) {
        String tenantId = requireTenant();
        Transfer t = transferService.createTransfer(
                TransferDirection.AS2_SEND, storageKey, TransferService.basename(storageKey));
        TransferJob job = new TransferJob(tenantId, t.getId().toString(), TransferDirection.AS2_SEND,
                storageKey, null, null, 0, null, null, null, partner.getId().toString());
        startWorkflow(t, job);
        return t;
    }

    private void startWorkflow(Transfer t, TransferJob job) {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(props.getTaskQueue())
                .setWorkflowId("transfer-" + t.getId())
                .build();
        TransferWorkflow workflow = workflowClient.newWorkflowStub(TransferWorkflow.class, options);
        WorkflowClient.start(workflow::run, job);
    }

    private String requireTenant() {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant bound to the request");
        }
        return tenantId;
    }
}

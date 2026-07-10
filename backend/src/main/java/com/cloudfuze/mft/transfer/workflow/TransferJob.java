package com.cloudfuze.mft.transfer.workflow;

import com.cloudfuze.mft.transfer.TransferDirection;

/**
 * The immutable input to a transfer workflow. Carries the tenant explicitly because Temporal
 * activities run on worker threads that have no request context — each activity re-binds the
 * tenant from this job.
 *
 * <p>SECURITY NOTE: SFTP credentials travel in this payload. It is protected at rest in Temporal
 * history by the AES-GCM {@code EncryptionCodec} on the workflow client. {@code expectedHostKey}
 * carries the partner's pinned host-key fingerprint so the activity enforces it (MITM defense).
 *
 * <p>{@code as2PartnerId} is set only for {@link TransferDirection#AS2_SEND} jobs; the SFTP fields
 * are unused in that case. The same {@code sftpHost/sftpPort/sftpUsername/sftpPassword} fields are
 * reused verbatim for {@link TransferDirection#FTPS_PULL}/{@code FTPS_PUSH} jobs too — FTPS and
 * SFTP share the exact same host/port/username/password shape, so a separate set of "ftps*" fields
 * would be pure duplication. A flat record with unused-per-direction fields is a deliberate scoping
 * call (not a polymorphic job type) — revisit if a shape genuinely incompatible with either joins.
 */
public record TransferJob(
        String tenantId,
        String transferId,
        TransferDirection direction,
        String sourceRef,
        String remotePath,
        String sftpHost,
        int sftpPort,
        String sftpUsername,
        String sftpPassword,
        String expectedHostKey,
        String as2PartnerId) {
}

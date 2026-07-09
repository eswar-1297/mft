package com.cloudfuze.mft.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A single file movement and its lifecycle. Tenant-scoped via {@code @TenantId}. This is the
 * operational record behind the dashboard's activity table, the retry/MTTR story, and the audit
 * trail (each state change is also written to the immutable audit log).
 */
@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TransferDirection direction;

    /** Source reference: an object-store key (PUSH) or a remote SFTP path (PULL). */
    @Column(name = "source_ref", nullable = false, updatable = false)
    private String sourceRef;

    /** Destination reference: a remote SFTP path (PUSH) or an object-store key (PULL). */
    @Column(name = "dest_ref")
    private String destRef;

    @Column(nullable = false, updatable = false)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Column(name = "bytes_transferred", nullable = false)
    private long bytesTransferred;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Transfer() {
    }

    public Transfer(UUID id, TransferDirection direction, String sourceRef, String filename,
                    String createdBy) {
        this.id = id;
        this.direction = direction;
        this.sourceRef = sourceRef;
        this.filename = filename;
        this.createdBy = createdBy;
        this.status = TransferStatus.PENDING;
        this.bytesTransferred = 0;
        this.attempts = 0;
        this.createdAt = Instant.now();
    }

    public void markRunning() {
        this.status = TransferStatus.RUNNING;
        this.startedAt = Instant.now();
        this.attempts++;
        this.errorMessage = null;
    }

    public void markSucceeded(String destRef, long bytes, String checksum) {
        this.status = TransferStatus.SUCCEEDED;
        this.destRef = destRef;
        this.bytesTransferred = bytes;
        this.checksumSha256 = checksum;
        this.completedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = TransferStatus.FAILED;
        this.errorMessage = error;
        this.completedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public TransferDirection getDirection() {
        return direction;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public String getDestRef() {
        return destRef;
    }

    public String getFilename() {
        return filename;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public long getBytesTransferred() {
        return bytesTransferred;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}

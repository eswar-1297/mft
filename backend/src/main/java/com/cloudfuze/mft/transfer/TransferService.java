package com.cloudfuze.mft.transfer;

import com.cloudfuze.mft.audit.AuditService;
import com.cloudfuze.mft.auth.AuthPrincipal;
import com.cloudfuze.mft.common.ApiException;
import com.cloudfuze.mft.common.Checksums;
import com.cloudfuze.mft.connector.SftpConnectionDetails;
import com.cloudfuze.mft.connector.SftpConnector;
import com.cloudfuze.mft.storage.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * The transfer engine's operations, decomposed so a durable Temporal workflow can drive them:
 * create the record, mark lifecycle transitions, and perform one physical transfer attempt. Each
 * step is short and independently retryable; the workflow (not this class) owns retry/backoff.
 *
 * <p>Every state change is written to the tamper-evident audit log. The physical transfer methods
 * do only I/O (no DB) and rely on the caller having bound the tenant in {@code TenantContext}.
 */
@Service
public class TransferService {

    private final TransferRepository transfers;
    private final StorageService storage;
    private final SftpConnector sftp;
    private final AuditService audit;

    public TransferService(TransferRepository transfers, StorageService storage,
                           SftpConnector sftp, AuditService audit) {
        this.transfers = transfers;
        this.storage = storage;
        this.sftp = sftp;
        this.audit = audit;
    }

    // --- lifecycle (DB + audit) ---

    /** Create a PENDING transfer record, attributed to the current user. Runs in the request thread. */
    @Transactional
    public Transfer createTransfer(TransferDirection direction, String sourceRef, String filename) {
        Transfer t = new Transfer(UUID.randomUUID(), direction, sourceRef, filename, currentUserEmail());
        transfers.save(t);
        audit.record("transfer.created", "transfer", t.getId().toString(),
                Map.of("direction", direction.name(), "filename", filename));
        return t;
    }

    @Transactional
    public void markRunning(UUID transferId) {
        Transfer t = load(transferId);
        t.markRunning();
        transfers.save(t);
        audit.record("transfer.started", "transfer", transferId.toString(),
                Map.of("attempt", t.getAttempts()));
    }

    @Transactional
    public void markSucceeded(UUID transferId, TransferResult result) {
        Transfer t = load(transferId);
        t.markSucceeded(result.destRef(), result.bytes(), result.checksum());
        transfers.save(t);
        // Include filename + destination so the audit log is answerable ("did <file> reach <dest>?").
        audit.record("transfer.completed", "transfer", transferId.toString(),
                Map.of("filename", t.getFilename(), "dest", nz(result.destRef()),
                        "bytes", result.bytes(), "sha256", result.checksum(), "attempts", t.getAttempts()));
    }

    @Transactional
    public void markFailed(UUID transferId, String error) {
        Transfer t = load(transferId);
        t.markFailed(error);
        transfers.save(t);
        audit.record("transfer.failed", "transfer", transferId.toString(),
                Map.of("filename", t.getFilename(), "error", error, "attempts", t.getAttempts()));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public void recordRetryScheduled(UUID transferId, int attempt, String reason) {
        audit.record("transfer.retry", "transfer", transferId.toString(),
                Map.of("attempt", attempt, "reason", reason));
    }

    // --- physical transfer (I/O only; one attempt) ---

    /** Pull a partner's file over SFTP into our object store. One attempt; throws on failure. */
    public TransferResult performPull(SftpConnectionDetails details, String remotePath) {
        String filename = basename(remotePath);
        Path stage = createStage("mft-pull-");
        try {
            sftp.download(details, remotePath, stage);
            StorageService.StoredObject stored = storage.putFile(stage, filename, null);
            return new TransferResult(stored.key(), stored.size(), stored.sha256());
        } finally {
            deleteQuietly(stage);
        }
    }

    /** Push one of our stored objects out to a partner over SFTP. One attempt; throws on failure. */
    public TransferResult performPush(String storageKey, SftpConnectionDetails details, String remotePath) {
        Path stage = createStage("mft-push-");
        try {
            storage.getToFile(storageKey, stage);
            long bytes = sftp.upload(details, remotePath, stage);
            return new TransferResult(remotePath, bytes, Checksums.sha256(stage));
        } finally {
            deleteQuietly(stage);
        }
    }

    // --- helpers ---

    private Transfer load(UUID id) {
        return transfers.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transfer not found"));
    }

    static String basename(String path) {
        if (path == null || path.isBlank()) {
            return "file";
        }
        String p = path.replace('\\', '/');
        int slash = p.lastIndexOf('/');
        String name = slash >= 0 ? p.substring(slash + 1) : p;
        return name.isBlank() ? "file" : name;
    }

    private static Path createStage(String prefix) {
        try {
            return Files.createTempFile(prefix, ".stage");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create staging file", e);
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // staging file cleanup is best-effort
        }
    }

    private String currentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) {
            return p.email();
        }
        return "system";
    }
}

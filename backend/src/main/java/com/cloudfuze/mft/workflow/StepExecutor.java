package com.cloudfuze.mft.workflow;

import com.cloudfuze.mft.common.ApiException;
import com.cloudfuze.mft.common.Checksums;
import com.cloudfuze.mft.connector.SftpConnectionDetails;
import com.cloudfuze.mft.connector.SftpConnector;
import com.cloudfuze.mft.connector.cloud.CloudConnector;
import com.cloudfuze.mft.connector.cloud.ConnectorService;
import com.cloudfuze.mft.connector.cloud.ConnectorClient;
import com.cloudfuze.mft.partner.Partner;
import com.cloudfuze.mft.partner.PartnerService;
import com.cloudfuze.mft.pgp.PgpService;
import com.cloudfuze.mft.storage.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Executes a single pipeline step against the current {@link Artifact}, returning the next
 * artifact. All real I/O lives here (object store, SFTP, PGP); the Temporal activity is a thin
 * wrapper that binds the tenant and calls this. Each step downloads the current artifact to a
 * staging file as needed and uploads any transformed result back to the object store.
 */
@Service
public class StepExecutor {

    private final StorageService storage;
    private final SftpConnector sftp;
    private final PartnerService partnerService;
    private final PgpService pgp;
    private final ConnectorService connectorService;

    public StepExecutor(StorageService storage, SftpConnector sftp, PartnerService partnerService,
                        PgpService pgp, ConnectorService connectorService) {
        this.storage = storage;
        this.sftp = sftp;
        this.partnerService = partnerService;
        this.pgp = pgp;
        this.connectorService = connectorService;
    }

    public Artifact execute(WorkflowStep step, Artifact current) {
        return switch (step.type()) {
            case PICKUP -> pickup(step);
            case PGP_ENCRYPT -> pgpTransform(step, current, true);
            case PGP_DECRYPT -> pgpTransform(step, current, false);
            case VALIDATE -> validate(step, current);
            case SEND -> send(step, current);
            case ARCHIVE -> archive(current);
            case NOTIFY -> current; // notification recorded by the activity via audit
        };
    }

    private Artifact pickup(WorkflowStep step) {
        String source = step.cfg("source", "STORAGE");
        if ("STORAGE".equalsIgnoreCase(source)) {
            String key = require(step, "storageKey");
            long size = storage.sizeOf(key);
            return new Artifact(key, filenameFromKey(key), null, size);
        }
        if ("CONNECTOR".equalsIgnoreCase(source)) {
            // Pull an object from an external S3-compatible connector into our object store.
            CloudConnector conn = connectorService.get(UUID.fromString(require(step, "connectorId")));
            String objectKey = require(step, "objectKey");
            Path stage = stage("pickup-conn");
            try (ConnectorClient client = connectorService.open(conn)) {
                client.download(objectKey, stage);
                StorageService.StoredObject stored = storage.putFile(stage, basename(objectKey), null);
                return new Artifact(stored.key(), basename(objectKey), stored.sha256(), stored.size());
            } finally {
                delete(stage);
            }
        }
        // SFTP pull from a partner
        Partner partner = partner(step);
        String remotePath = require(step, "remotePath");
        SftpConnectionDetails details = partnerService.toConnectionDetails(partner, step.cfg("password"));
        Path stage = stage("pickup");
        try {
            sftp.download(details, remotePath, stage);
            StorageService.StoredObject stored = storage.putFile(stage, basename(remotePath), null);
            return new Artifact(stored.key(), basename(remotePath), stored.sha256(), stored.size());
        } finally {
            delete(stage);
        }
    }

    private Artifact pgpTransform(WorkflowStep step, Artifact current, boolean encrypt) {
        requireArtifact(current);
        char[] passphrase = require(step, "passphrase").toCharArray();
        Path in = stage("pgp-in");
        Path out = stage("pgp-out");
        try {
            storage.getToFile(current.key(), in);
            if (encrypt) {
                pgp.encrypt(in, out, passphrase);
            } else {
                pgp.decrypt(in, out, passphrase);
            }
            String name = encrypt ? current.filename() + ".pgp"
                    : current.filename().replaceAll("\\.pgp$", "");
            StorageService.StoredObject stored =
                    storage.putFileAt(out, UUID.randomUUID() + "-" + name);
            return new Artifact(stored.key(), name, stored.sha256(), stored.size());
        } finally {
            delete(in);
            delete(out);
        }
    }

    private Artifact validate(WorkflowStep step, Artifact current) {
        requireArtifact(current);
        long size = current.bytes() > 0 ? current.bytes() : storage.sizeOf(current.key());
        long min = parseLong(step.cfg("minBytes"), 1);
        long max = parseLong(step.cfg("maxBytes"), Long.MAX_VALUE);
        if (size < min) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Validation failed: file is " + size + " bytes, below minimum " + min);
        }
        if (size > max) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Validation failed: file is " + size + " bytes, above maximum " + max);
        }
        return current;
    }

    private Artifact send(WorkflowStep step, Artifact current) {
        requireArtifact(current);
        String dest = step.cfg("dest", "SFTP");
        if ("STORAGE".equalsIgnoreCase(dest)) {
            return current; // already in the object store
        }
        if ("CONNECTOR".equalsIgnoreCase(dest)) {
            // Deliver the current artifact to an external S3-compatible connector.
            CloudConnector conn = connectorService.get(UUID.fromString(require(step, "connectorId")));
            String objectKey = require(step, "objectKey");
            Path stage = stage("send-conn");
            try (ConnectorClient client = connectorService.open(conn)) {
                storage.getToFile(current.key(), stage);
                long bytes = client.upload(objectKey, stage);
                return new Artifact(current.key(), current.filename(), Checksums.sha256(stage), bytes);
            } finally {
                delete(stage);
            }
        }
        Partner partner = partner(step);
        String remotePath = require(step, "remotePath");
        SftpConnectionDetails details = partnerService.toConnectionDetails(partner, step.cfg("password"));
        Path stage = stage("send");
        try {
            storage.getToFile(current.key(), stage);
            long bytes = sftp.upload(details, remotePath, stage);
            return new Artifact(current.key(), current.filename(), Checksums.sha256(stage), bytes);
        } finally {
            delete(stage);
        }
    }

    private Artifact archive(Artifact current) {
        requireArtifact(current);
        Path stage = stage("archive");
        try {
            storage.getToFile(current.key(), stage);
            StorageService.StoredObject stored =
                    storage.putFileAt(stage, "archive/" + UUID.randomUUID() + "-" + current.filename());
            return new Artifact(stored.key(), current.filename(), stored.sha256(), stored.size());
        } finally {
            delete(stage);
        }
    }

    // --- helpers ---

    private Partner partner(WorkflowStep step) {
        String partnerId = require(step, "partnerId");
        return partnerService.get(UUID.fromString(partnerId));
    }

    private static String require(WorkflowStep step, String key) {
        String v = step.cfg(key);
        if (v == null || v.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Step " + step.type() + " is missing required config '" + key + "'");
        }
        return v;
    }

    private static void requireArtifact(Artifact a) {
        if (a == null || a.key() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "This step needs a file, but no PICKUP produced one earlier in the pipeline");
        }
    }

    private static long parseLong(String s, long dflt) {
        try {
            return s == null || s.isBlank() ? dflt : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private static String basename(String path) {
        String p = path.replace('\\', '/');
        int slash = p.lastIndexOf('/');
        String name = slash >= 0 ? p.substring(slash + 1) : p;
        return name.isBlank() ? "file" : name;
    }

    private static String filenameFromKey(String key) {
        String base = basename(key);
        // Stored keys look like "<uuid>-<name>"; strip the uuid prefix if present.
        int dash = base.indexOf('-', 36);
        return dash > 0 && base.length() > 37 ? base.substring(dash + 1) : base;
    }

    private static Path stage(String prefix) {
        try {
            return Files.createTempFile("mft-" + prefix + "-", ".stage");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create staging file", e);
        }
    }

    private static void delete(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best-effort
        }
    }
}

package com.cloudfuze.mft.storage;

import com.cloudfuze.mft.common.Checksums;
import com.cloudfuze.mft.tenant.TenantContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Reads and writes file payloads in the object store. Keys are namespaced by tenant so one
 * customer's objects can never collide with or be addressed as another's. Every stored object's
 * SHA-256 is computed and returned so the transfer engine can prove integrity end-to-end.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final S3Client s3;
    private final StorageProperties props;

    @org.springframework.beans.factory.annotation.Value("${mft.startup.connect-external:true}")
    private boolean connectExternal;

    public StorageService(S3Client s3, StorageProperties props) {
        this.s3 = s3;
        this.props = props;
    }

    /** The result of storing an object: its key, content hash, and byte size. */
    public record StoredObject(String key, String sha256, long size) {
    }

    @PostConstruct
    void ensureBucket() {
        // Allows the app (and integration tests) to start without the object store reachable.
        if (!connectExternal) {
            return;
        }
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(props.getBucket()).build());
        } catch (NoSuchBucketException e) {
            log.info("Creating object store bucket '{}'", props.getBucket());
            s3.createBucket(CreateBucketRequest.builder().bucket(props.getBucket()).build());
        }
    }

    /** Upload a staged local file, returning its tenant-scoped key and checksum. */
    public StoredObject putFile(Path file, String filename, String contentType) {
        String key = newKey(filename);
        long size = fileSize(file);
        s3.putObject(PutObjectRequest.builder()
                        .bucket(props.getBucket())
                        .key(key)
                        .contentType(contentType != null ? contentType : "application/octet-stream")
                        .contentLength(size)
                        .build(),
                RequestBody.fromFile(file));
        return new StoredObject(key, Checksums.sha256(file), size);
    }

    /** Download an object to a local staging file. */
    public void getToFile(String key, Path target) {
        // AWS SDK's toFile(Path) uses CREATE_NEW and fails if the file already exists; our caller
        // pre-creates the staging file with createTempFile, so remove that placeholder first.
        try {
            java.nio.file.Files.deleteIfExists(target);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot prepare staging file " + target, e);
        }
        s3.getObject(GetObjectRequest.builder().bucket(props.getBucket()).key(key).build(),
                ResponseTransformer.toFile(target));
    }

    public long sizeOf(String key) {
        return s3.headObject(HeadObjectRequest.builder()
                .bucket(props.getBucket()).key(key).build()).contentLength();
    }

    /**
     * Store a local file under a caller-chosen key suffix within the current tenant's namespace
     * (used by pipeline steps for transformed artifacts and archives). Returns the full key + hash.
     */
    public StoredObject putFileAt(Path file, String keySuffix) {
        String tenant = TenantContext.get();
        if (tenant == null) {
            throw new IllegalStateException("No tenant bound; cannot key an object");
        }
        String key = tenant + "/" + keySuffix;
        long size = fileSize(file);
        s3.putObject(PutObjectRequest.builder()
                        .bucket(props.getBucket())
                        .key(key)
                        .contentLength(size)
                        .build(),
                RequestBody.fromFile(file));
        return new StoredObject(key, Checksums.sha256(file), size);
    }

    private String newKey(String filename) {
        String tenant = TenantContext.get();
        if (tenant == null) {
            throw new IllegalStateException("No tenant bound; cannot key an object");
        }
        String safe = filename == null ? "file" : filename.replaceAll("[^A-Za-z0-9._-]", "_");
        return tenant + "/" + UUID.randomUUID() + "-" + safe;
    }

    private long fileSize(Path file) {
        try {
            return java.nio.file.Files.size(file);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot size staged file " + file, e);
        }
    }
}

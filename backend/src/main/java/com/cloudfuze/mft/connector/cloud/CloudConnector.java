package com.cloudfuze.mft.connector.cloud;

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
 * A configured external endpoint (e.g. an S3 bucket at a partner or in the customer's own cloud)
 * usable as a transfer source or destination. Tenant-scoped. Non-secret config lives in columns;
 * the secret (e.g. S3 secret key) is stored only as vault-encrypted ciphertext.
 */
@Entity
@Table(name = "connectors")
public class CloudConnector {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectorType type;

    /** S3: endpoint URL (blank = real AWS). */
    @Column
    private String endpoint;

    /** S3: region. */
    @Column
    private String region;

    /** S3: bucket name. */
    @Column
    private String bucket;

    /** S3: access key id (not itself secret, but paired with the secret below). */
    @Column(name = "access_key")
    private String accessKey;

    /** Vault-encrypted secret (S3 secret key). Never returned by the API. */
    @Column(name = "secret_enc", columnDefinition = "text")
    private String secretEnc;

    /** S3: path-style access (true for MinIO/most compatibles, false for real AWS). */
    @Column(name = "path_style", nullable = false)
    private boolean pathStyle = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected CloudConnector() {
    }

    public CloudConnector(UUID id, String name, ConnectorType type, String endpoint, String region,
                          String bucket, String accessKey, String secretEnc, boolean pathStyle) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.endpoint = endpoint;
        this.region = region;
        this.bucket = bucket;
        this.accessKey = accessKey;
        this.secretEnc = secretEnc;
        this.pathStyle = pathStyle;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public ConnectorType getType() {
        return type;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getRegion() {
        return region;
    }

    public String getBucket() {
        return bucket;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretEnc() {
        return secretEnc;
    }

    public boolean isPathStyle() {
        return pathStyle;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

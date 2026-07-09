package com.cloudfuze.mft.partner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A trading partner and its connection profile. Tenant-scoped via {@code @TenantId}. The SFTP
 * password is stored only as vault-encrypted ciphertext ({@code secretEnc}); it is never persisted
 * or returned in plaintext.
 */
@Entity
@Table(name = "partners")
public class Partner {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    /** Protocol label, e.g. "SFTP". (AS2/FTPS join as those connectors land.) */
    @Column(nullable = false)
    private String protocol;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(nullable = false)
    private String username;

    /** AES-GCM ciphertext of the SFTP password (see CryptoVault). Never exposed via the API. */
    @Column(name = "secret_enc", columnDefinition = "text")
    private String secretEnc;

    /** Pinned SFTP host-key fingerprint (e.g. "SHA256:abc…"), or null if not yet pinned. */
    @Column(name = "host_key_fingerprint")
    private String hostKeyFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Partner() {
    }

    public Partner(UUID id, String name, String protocol, String host, int port,
                   String username, String secretEnc) {
        this.id = id;
        this.name = name;
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.username = username;
        this.secretEnc = secretEnc;
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

    public String getProtocol() {
        return protocol;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getSecretEnc() {
        return secretEnc;
    }

    public String getHostKeyFingerprint() {
        return hostKeyFingerprint;
    }

    public void setHostKeyFingerprint(String hostKeyFingerprint) {
        this.hostKeyFingerprint = hostKeyFingerprint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean hasStoredSecret() {
        return secretEnc != null && !secretEnc.isBlank();
    }

    public boolean hasPinnedHostKey() {
        return hostKeyFingerprint != null && !hostKeyFingerprint.isBlank();
    }
}

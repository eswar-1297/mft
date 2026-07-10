package com.cloudfuze.mft.as2;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * This tenant's own AS2 identity: the AS2-From ID we present to partners, our self-signed
 * certificate (public — sent to partners so they can verify our signature and encrypt to us), and
 * our private key (vault-encrypted, used to sign outbound messages and decrypt inbound ones).
 * Exactly one row per tenant, created lazily on first AS2 use.
 */
@Entity
@Table(name = "as2_identities")
public class As2Identity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    /** The AS2-From identifier this tenant presents to partners. */
    @Column(name = "as2_id", nullable = false)
    private String as2Id;

    /** Our public certificate (PEM). Not secret — handed to partners so they can trust us. */
    @Column(name = "certificate_pem", nullable = false, columnDefinition = "text")
    private String certificatePem;

    /** Vault-encrypted PEM private key. Never returned by the API. */
    @Column(name = "private_key_enc", nullable = false, columnDefinition = "text")
    private String privateKeyEnc;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected As2Identity() {
    }

    public As2Identity(UUID id, String as2Id, String certificatePem, String privateKeyEnc) {
        this.id = id;
        this.as2Id = as2Id;
        this.certificatePem = certificatePem;
        this.privateKeyEnc = privateKeyEnc;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getAs2Id() {
        return as2Id;
    }

    public String getCertificatePem() {
        return certificatePem;
    }

    public String getPrivateKeyEnc() {
        return privateKeyEnc;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

package com.cloudfuze.mft.as2;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A configured AS2 trading partner: their AS2-From identifier, their public certificate (used to
 * verify their signature and encrypt messages to them), and the URL we POST outbound messages to.
 * Tenant-scoped. The certificate is exchanged out-of-band and pinned directly — the same
 * trust-on-first-use model already used for SFTP partner host keys.
 */
@Entity
@Table(name = "as2_partners")
public class As2Partner {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    /** The AS2-From identifier this partner presents in messages they send us. */
    @Column(name = "partner_as2_id", nullable = false)
    private String partnerAs2Id;

    /** The partner's public certificate (PEM) — not secret. */
    @Column(name = "partner_certificate_pem", nullable = false, columnDefinition = "text")
    private String partnerCertificatePem;

    /** The partner's AS2 receiving endpoint we POST outbound messages to. */
    @Column(name = "inbound_url", nullable = false)
    private String inboundUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected As2Partner() {
    }

    public As2Partner(UUID id, String name, String partnerAs2Id, String partnerCertificatePem,
                      String inboundUrl) {
        this.id = id;
        this.name = name;
        this.partnerAs2Id = partnerAs2Id;
        this.partnerCertificatePem = partnerCertificatePem;
        this.inboundUrl = inboundUrl;
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

    public String getPartnerAs2Id() {
        return partnerAs2Id;
    }

    public String getPartnerCertificatePem() {
        return partnerCertificatePem;
    }

    public String getInboundUrl() {
        return inboundUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

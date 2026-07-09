package com.cloudfuze.mft.siem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A tenant's SIEM forwarding destination. One per tenant. Intentionally NOT {@code @TenantId}-
 * scoped: the system forwarder enumerates all destinations across tenants, then sets the tenant
 * context per destination when reading that tenant's audit events.
 */
@Entity
@Table(name = "siem_destinations")
public class SiemDestination {

    public enum Type {HTTP, SYSLOG_TCP}

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false, unique = true)
    private String tenantId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private String type;

    /** HTTP: full URL. SYSLOG_TCP: host:port. */
    @Column(nullable = false)
    private String target;

    /** Vault-encrypted bearer token for HTTP destinations (e.g. a Splunk HEC token). */
    @Column(name = "token_enc", columnDefinition = "text")
    private String tokenEnc;

    /** Cursor: highest audit seq already shipped to this destination. */
    @Column(name = "last_forwarded_seq", nullable = false)
    private long lastForwardedSeq;

    @Column(name = "last_status")
    private String lastStatus;

    @Column(name = "last_forwarded_at")
    private Instant lastForwardedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected SiemDestination() {
    }

    public SiemDestination(UUID id, String tenantId) {
        this.id = id;
        this.tenantId = tenantId;
        this.enabled = false;
        this.type = Type.HTTP.name();
        this.target = "";
        this.lastForwardedSeq = 0;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Type typeEnum() {
        return Type.valueOf(type);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getTokenEnc() {
        return tokenEnc;
    }

    public void setTokenEnc(String tokenEnc) {
        this.tokenEnc = tokenEnc;
    }

    public long getLastForwardedSeq() {
        return lastForwardedSeq;
    }

    public void setLastForwardedSeq(long lastForwardedSeq) {
        this.lastForwardedSeq = lastForwardedSeq;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }

    public Instant getLastForwardedAt() {
        return lastForwardedAt;
    }

    public void setLastForwardedAt(Instant lastForwardedAt) {
        this.lastForwardedAt = lastForwardedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

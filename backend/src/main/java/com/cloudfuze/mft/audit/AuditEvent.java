package com.cloudfuze.mft.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * One immutable, append-only audit record. There is deliberately no setter and no update or
 * delete path in the repository or API — records are written once and thereafter only read or
 * verified. Each row stores the hash of the previous row, forming the tamper-evident chain.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    /** Per-tenant monotonic sequence, starting at 1. Ordering key for the chain. */
    @Column(nullable = false, updatable = false)
    private long seq;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_id", updatable = false)
    private String actorId;

    @Column(name = "actor_email", updatable = false)
    private String actorEmail;

    @Column(nullable = false, updatable = false)
    private String action;

    @Column(name = "resource_type", updatable = false)
    private String resourceType;

    @Column(name = "resource_id", updatable = false)
    private String resourceId;

    /** Canonical (key-sorted) JSON of extra context. Part of the hashed payload. */
    @Column(name = "details_json", columnDefinition = "text", updatable = false)
    private String detailsJson;

    @Column(name = "prev_hash", nullable = false, updatable = false, length = 64)
    private String prevHash;

    @Column(nullable = false, updatable = false, length = 64)
    private String hash;

    protected AuditEvent() {
    }

    AuditEvent(UUID id, long seq, Instant occurredAt, String actorId, String actorEmail,
               String action, String resourceType, String resourceId, String detailsJson,
               String prevHash, String hash) {
        this.id = id;
        this.seq = seq;
        this.occurredAt = occurredAt;
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.detailsJson = detailsJson;
        this.prevHash = prevHash;
        this.hash = hash;
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public long getSeq() {
        return seq;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public String getHash() {
        return hash;
    }
}

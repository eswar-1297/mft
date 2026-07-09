package com.cloudfuze.mft.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * The current tip of a tenant's audit chain. Appending an event takes a pessimistic write lock
 * on this row, which serializes concurrent appends per tenant — essential, because a hash chain
 * requires each record to point at exactly the one before it. Two parallel appends without this
 * lock would fork the chain and corrupt it.
 *
 * <p>Keyed directly by tenant id (not {@code @TenantId}-scoped) so the lock lookup is a simple
 * primary-key read.
 */
@Entity
@Table(name = "audit_chain_head")
public class AuditChainHead {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq;

    @Column(name = "last_hash", nullable = false, length = 64)
    private String lastHash;

    @Version
    private long version;

    protected AuditChainHead() {
    }

    public AuditChainHead(String tenantId) {
        this.tenantId = tenantId;
        this.lastSeq = 0;
        this.lastHash = AuditHasher.GENESIS_HASH;
    }

    public String getTenantId() {
        return tenantId;
    }

    public long getLastSeq() {
        return lastSeq;
    }

    public String getLastHash() {
        return lastHash;
    }

    public void advance(long seq, String hash) {
        this.lastSeq = seq;
        this.lastHash = hash;
    }
}

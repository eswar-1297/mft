package com.cloudfuze.mft.tenantmodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A customer organization. This is the ONE entity that is intentionally global (not
 * {@code @TenantId}-scoped): it is the registry that logins and provisioning resolve against.
 */
@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** URL/login-safe identifier, e.g. "hdfc-bank". Unique across the platform. */
    @Column(nullable = false, unique = true)
    private String slug;

    /** Commercial tier: STARTER / BUSINESS / ENTERPRISE. */
    @Column(nullable = false)
    private String plan = "STARTER";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Tenant() {
    }

    public Tenant(UUID id, String name, String slug, String plan) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.plan = plan;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getPlan() {
        return plan;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }
}

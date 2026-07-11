package com.cloudfuze.mft.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A saved, reusable pipeline definition. The ordered steps are stored as JSON in {@code stepsJson}.
 * Tenant-scoped via {@code @TenantId}. Named {@code WorkflowDef} to avoid clashing with Temporal's
 * workflow types.
 */
@Entity
@Table(name = "workflows")
public class WorkflowDef {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "steps_json", columnDefinition = "text", nullable = false)
    private String stepsJson;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_status")
    private String lastStatus;

    /** Standard 5-field cron (UTC), or null when the workflow is not scheduled. */
    @Column(name = "cron_schedule")
    private String cronSchedule;

    /** When set, this workflow auto-runs the moment this AS2 partner sends us a message. */
    @Column(name = "as2_trigger_partner_id")
    private UUID as2TriggerPartnerId;

    protected WorkflowDef() {
    }

    public WorkflowDef(UUID id, String name, String stepsJson) {
        this.id = id;
        this.name = name;
        this.stepsJson = stepsJson;
        this.enabled = true;
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

    public String getStepsJson() {
        return stepsJson;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void recordRun(String status) {
        this.lastRunAt = Instant.now();
        this.lastStatus = status;
    }

    public String getCronSchedule() {
        return cronSchedule;
    }

    public void setCronSchedule(String cronSchedule) {
        this.cronSchedule = cronSchedule;
    }

    public UUID getAs2TriggerPartnerId() {
        return as2TriggerPartnerId;
    }

    public void setAs2TriggerPartnerId(UUID as2TriggerPartnerId) {
        this.as2TriggerPartnerId = as2TriggerPartnerId;
    }
}

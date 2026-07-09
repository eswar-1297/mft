package com.cloudfuze.mft.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One execution of a {@link WorkflowDef}. Per-step outcomes are stored as JSON in {@code stepResultsJson}. */
@Entity
@Table(name = "workflow_runs")
public class WorkflowRun {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "workflow_id", nullable = false, updatable = false)
    private UUID workflowId;

    @Column(name = "workflow_name", nullable = false, updatable = false)
    private String workflowName;

    @Column(nullable = false)
    private String status;

    @Column(name = "step_results_json", columnDefinition = "text")
    private String stepResultsJson;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    protected WorkflowRun() {
    }

    public WorkflowRun(UUID id, UUID workflowId, String workflowName) {
        this.id = id;
        this.workflowId = workflowId;
        this.workflowName = workflowName;
        this.status = "RUNNING";
        this.startedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public String getStatus() {
        return status;
    }

    public String getStepResultsJson() {
        return stepResultsJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void succeed(String stepResultsJson) {
        this.status = "SUCCEEDED";
        this.stepResultsJson = stepResultsJson;
        this.completedAt = Instant.now();
    }

    public void fail(String stepResultsJson, String error) {
        this.status = "FAILED";
        this.stepResultsJson = stepResultsJson;
        this.errorMessage = error;
        this.completedAt = Instant.now();
    }
}

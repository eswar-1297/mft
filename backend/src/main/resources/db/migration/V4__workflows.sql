-- Saved multi-step pipeline definitions and their run history.
CREATE TABLE workflows (
    id          UUID        PRIMARY KEY,
    tenant_id   TEXT        NOT NULL,
    name        TEXT        NOT NULL,
    steps_json  TEXT        NOT NULL,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL,
    last_run_at TIMESTAMPTZ,
    last_status TEXT,
    CONSTRAINT uq_workflows_tenant_name UNIQUE (tenant_id, name)
);
CREATE INDEX idx_workflows_tenant ON workflows (tenant_id);

CREATE TABLE workflow_runs (
    id                UUID        PRIMARY KEY,
    tenant_id         TEXT        NOT NULL,
    workflow_id       UUID        NOT NULL,
    workflow_name     TEXT        NOT NULL,
    status            TEXT        NOT NULL,
    step_results_json TEXT,
    error_message     TEXT,
    started_at        TIMESTAMPTZ NOT NULL,
    completed_at      TIMESTAMPTZ
);
CREATE INDEX idx_workflow_runs_tenant_started ON workflow_runs (tenant_id, started_at DESC);
CREATE INDEX idx_workflow_runs_workflow ON workflow_runs (tenant_id, workflow_id);

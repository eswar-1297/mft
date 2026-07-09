-- Transfer engine: the operational record of every file movement.
CREATE TABLE transfers (
    id                UUID        PRIMARY KEY,
    tenant_id         TEXT        NOT NULL,
    direction         TEXT        NOT NULL,
    source_ref        TEXT        NOT NULL,
    dest_ref          TEXT,
    filename          TEXT        NOT NULL,
    status            TEXT        NOT NULL,
    bytes_transferred BIGINT      NOT NULL DEFAULT 0,
    checksum_sha256   VARCHAR(64),
    error_message     TEXT,
    attempts          INTEGER     NOT NULL DEFAULT 0,
    created_by        TEXT,
    created_at        TIMESTAMPTZ NOT NULL,
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ
);
CREATE INDEX idx_transfers_tenant_created ON transfers (tenant_id, created_at DESC);
CREATE INDEX idx_transfers_tenant_status ON transfers (tenant_id, status);

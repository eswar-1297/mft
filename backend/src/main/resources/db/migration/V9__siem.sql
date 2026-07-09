-- Per-tenant SIEM forwarding destination. The forwarder ships audit events with seq >
-- last_forwarded_seq to the target, advancing the cursor only on success (at-least-once, no loss).
-- tenant_id is a plain column (not the @TenantId discriminator) so the system forwarder can
-- enumerate destinations across all tenants.
CREATE TABLE siem_destinations (
    id                 UUID        PRIMARY KEY,
    tenant_id          TEXT        NOT NULL UNIQUE,
    enabled            BOOLEAN     NOT NULL DEFAULT FALSE,
    type               TEXT        NOT NULL,          -- HTTP | SYSLOG_TCP
    target             TEXT        NOT NULL,          -- URL, or host:port for syslog
    token_enc          TEXT,                          -- vault-encrypted bearer token (HTTP)
    last_forwarded_seq BIGINT      NOT NULL DEFAULT 0,
    last_status        TEXT,
    last_forwarded_at  TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL
);

-- CloudFuze MFT — initial schema.
-- Flyway is the single source of truth for the database schema.

-- Customer organizations. Global registry (not tenant-scoped): logins resolve against it.
CREATE TABLE tenants (
    id         UUID        PRIMARY KEY,
    name       TEXT        NOT NULL,
    slug       TEXT        NOT NULL UNIQUE,
    plan       TEXT        NOT NULL DEFAULT 'STARTER',
    created_at TIMESTAMPTZ NOT NULL
);

-- Platform users, isolated per tenant via the tenant_id discriminator.
CREATE TABLE users (
    id            UUID        PRIMARY KEY,
    tenant_id     TEXT        NOT NULL,
    email         TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,
    full_name     TEXT        NOT NULL,
    role          TEXT        NOT NULL,
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email)
);
CREATE INDEX idx_users_tenant ON users (tenant_id);

-- The tip of each tenant's audit chain. A pessimistic lock on the row serializes appends.
CREATE TABLE audit_chain_head (
    tenant_id TEXT        PRIMARY KEY,
    last_seq  BIGINT      NOT NULL DEFAULT 0,
    last_hash VARCHAR(64) NOT NULL,
    version   BIGINT      NOT NULL DEFAULT 0
);

-- Immutable, append-only, tamper-evident audit log. No UPDATE/DELETE ever occurs here.
CREATE TABLE audit_events (
    id            UUID        PRIMARY KEY,
    tenant_id     TEXT        NOT NULL,
    seq           BIGINT      NOT NULL,
    occurred_at   TIMESTAMPTZ NOT NULL,
    actor_id      TEXT,
    actor_email   TEXT,
    action        TEXT        NOT NULL,
    resource_type TEXT,
    resource_id   TEXT,
    details_json  TEXT,
    prev_hash     VARCHAR(64) NOT NULL,
    hash          VARCHAR(64) NOT NULL,
    CONSTRAINT uq_audit_tenant_seq UNIQUE (tenant_id, seq)
);
CREATE INDEX idx_audit_tenant_seq ON audit_events (tenant_id, seq);
CREATE INDEX idx_audit_action ON audit_events (tenant_id, action);

-- NOTE (defense-in-depth, roadmap): PostgreSQL row-level security policies keyed on
-- current_setting('app.tenant_id') can be enabled on users/audit_events so the DATABASE
-- enforces tenant isolation even if the application layer is bypassed. Deferred until the
-- connection-level tenant GUC is wired, so it does not silently return empty result sets.

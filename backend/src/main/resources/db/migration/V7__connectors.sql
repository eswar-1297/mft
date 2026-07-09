-- External cloud connectors (S3 today; Azure/Drive/SharePoint/Box planned). The secret is stored
-- only as vault-encrypted ciphertext.
CREATE TABLE connectors (
    id         UUID        PRIMARY KEY,
    tenant_id  TEXT        NOT NULL,
    name       TEXT        NOT NULL,
    type       TEXT        NOT NULL,
    endpoint   TEXT,
    region     TEXT,
    bucket     TEXT,
    access_key TEXT,
    secret_enc TEXT,
    path_style BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_connectors_tenant_name UNIQUE (tenant_id, name)
);
CREATE INDEX idx_connectors_tenant ON connectors (tenant_id);

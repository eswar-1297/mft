-- Trading partners and their connection profiles. Secrets are stored only as vault-encrypted
-- ciphertext (secret_enc); never in plaintext.
CREATE TABLE partners (
    id         UUID        PRIMARY KEY,
    tenant_id  TEXT        NOT NULL,
    name       TEXT        NOT NULL,
    protocol   TEXT        NOT NULL,
    host       TEXT        NOT NULL,
    port       INTEGER     NOT NULL,
    username   TEXT        NOT NULL,
    secret_enc TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_partners_tenant_name UNIQUE (tenant_id, name)
);
CREATE INDEX idx_partners_tenant ON partners (tenant_id);

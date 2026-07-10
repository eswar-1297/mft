-- AS2 support: this tenant's own identity (one row per tenant, lazily created) and configured AS2
-- trading partners. Certificates are public and stored plaintext; private keys are stored only as
-- vault-encrypted ciphertext, exactly like partner/connector secrets elsewhere in this schema.

CREATE TABLE as2_identities (
    id               UUID        PRIMARY KEY,
    tenant_id        TEXT        NOT NULL,
    as2_id           TEXT        NOT NULL,
    certificate_pem  TEXT        NOT NULL,
    private_key_enc  TEXT        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_as2_identities_tenant UNIQUE (tenant_id)
);
CREATE INDEX idx_as2_identities_tenant ON as2_identities (tenant_id);

CREATE TABLE as2_partners (
    id                       UUID        PRIMARY KEY,
    tenant_id                TEXT        NOT NULL,
    name                     TEXT        NOT NULL,
    partner_as2_id           TEXT        NOT NULL,
    partner_certificate_pem  TEXT        NOT NULL,
    inbound_url              TEXT        NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_as2_partners_tenant_name UNIQUE (tenant_id, name)
);
CREATE INDEX idx_as2_partners_tenant ON as2_partners (tenant_id);

-- Row-level security, matching the V10 pattern (FORCE required since the app connects as owner).
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['as2_identities', 'as2_partners'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I '
            || 'USING (tenant_id = current_setting(''app.tenant_id'', true)) '
            || 'WITH CHECK (tenant_id = current_setting(''app.tenant_id'', true))', t);
    END LOOP;
END $$;

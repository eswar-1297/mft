-- Database-enforced tenant isolation (defense-in-depth). Even if application code loads a row by
-- primary key (which Hibernate's @TenantId does NOT filter), PostgreSQL itself refuses to return
-- or modify rows belonging to another tenant.
--
-- Mechanism: each tenant-scoped table gets a policy comparing its tenant_id to the connection GUC
-- `app.tenant_id`, which the application sets per connection from the request's TenantContext
-- (see TenantAwareDataSource). `current_setting(..., true)` returns NULL when unset, so an
-- unscoped connection matches NO rows — fail-closed.
--
-- FORCE is required because the app connects as the table owner, and owners bypass plain RLS.
-- Applied only to customer-data entity tables. Deliberately NOT applied to:
--   * tenants            — the global registry logins resolve against (pre-tenant-context)
--   * audit_chain_head   — internal per-tenant hash cursor (no customer data)
--   * siem_destinations  — read cross-tenant by the system SIEM forwarder
--   * flyway_schema_history

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'users', 'audit_events', 'transfers', 'partners', 'workflows', 'workflow_runs', 'connectors'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I '
            || 'USING (tenant_id = current_setting(''app.tenant_id'', true)) '
            || 'WITH CHECK (tenant_id = current_setting(''app.tenant_id'', true))', t);
    END LOOP;
END $$;

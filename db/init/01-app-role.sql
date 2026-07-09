-- Provisions the RUNTIME database role the application connects as.
--
-- Why two roles: PostgreSQL row-level security (see migration V10) is BYPASSED by superusers and
-- table owners. Migrations run as the owner/admin (creates tables, enables + FORCEs RLS); the
-- application runtime must connect as this separate NON-superuser role so RLS actually enforces
-- tenant isolation. Run automatically by the postgres container on first init (compose mounts it
-- into /docker-entrypoint-initdb.d), and by CI before tests. In managed prod, your DBA/Terraform
-- provisions the equivalent role.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'mft_app') THEN
        CREATE ROLE mft_app LOGIN PASSWORD 'mft_app';
    END IF;
END $$;

GRANT USAGE ON SCHEMA public TO mft_app;

-- Tables the admin/owner creates LATER (via Flyway migrations) are auto-granted to the runtime
-- role, so we never need to re-run grants after each migration.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO mft_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO mft_app;

-- Cover any tables that already exist (idempotent).
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO mft_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO mft_app;

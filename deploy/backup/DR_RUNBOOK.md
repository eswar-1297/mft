# Backup & Disaster Recovery Runbook — CloudFuze MFT

This runbook covers backing up and recovering all durable state. It is written for on-prem
operators and for the SaaS on-call. Keep it current — auditors (SOC 2 CC7.x / A1.2) and pilot
security reviews will ask for it, and will ask when it was last tested.

## What state exists

| Store | Contains | Backup mechanism |
|-------|----------|------------------|
| **PostgreSQL** | Tenants, users, credentials (encrypted), audit log (hash-chained), workflows, transfer records, connectors | `pg_dump -Fc` (logical) + optional WAL archiving (PITR) |
| **Object store (S3/MinIO)** | Transferred file payloads staged during pipelines | `mc mirror` to versioned, object-locked bucket |
| **Secrets (KMS/Vault)** | Master keys (JWT, crypto vault, DB) | Backed up by the KMS/Vault provider itself — **never** in these dumps |

The database dump contains encrypted credentials but **not** the keys that decrypt them. A restore
is only usable together with the corresponding KMS/Vault material — this is intentional
defence-in-depth. Store key backups separately from data backups.

## Objectives

| Metric | Target (pilot) | How met |
|--------|----------------|---------|
| **RPO** (max data loss) | ≤ 24h with daily logical backups; ≤ 5 min with WAL archiving / managed PITR | Schedule `backup.sh` daily; enable WAL archiving for tighter RPO |
| **RTO** (max downtime) | ≤ 1h for on-prem single node | `restore.sh` into a freshly provisioned instance |

For regulated production, run `backup.sh` at least daily via cron/k8s CronJob **and** enable
Postgres WAL archiving (or use a managed PITR-capable database) to hit a 5-minute RPO.

## Taking a backup

```bash
export PGHOST=... PGUSER=mft PGPASSWORD=... PGDATABASE=mft
export S3_ENDPOINT=... S3_ACCESS_KEY=... S3_SECRET_KEY=... S3_BUCKET=mft-files
export BACKUP_DIR=/secure/offhost/backups
./deploy/backup/backup.sh
```

Produces `BACKUP_DIR/<UTC-timestamp>/` containing `postgres.dump`, `objectstore/`, and
`MANIFEST.txt` (with a SHA-256 of the dump). **Ship this off-host** to encrypted, versioned,
access-controlled storage (e.g. S3 with Object Lock). A backup on the same host as the database is
not a backup.

## Restoring (disaster recovery)

Restore into a **freshly provisioned, empty** database/object store — never over live data without
a maintenance window.

```bash
# 1. Provision a clean Postgres + object store (docker compose up postgres minio, or managed).
# 2. Verify integrity against the manifest:
sha256sum backups/<stamp>/postgres.dump   # compare to MANIFEST.txt
# 3. Restore:
export PGHOST=... PGUSER=mft PGPASSWORD=... PGDATABASE=mft
export S3_ENDPOINT=... S3_ACCESS_KEY=... S3_SECRET_KEY=... S3_BUCKET=mft-files
./deploy/backup/restore.sh backups/<stamp>
# 4. Ensure the SAME KMS/Vault key material is wired (see SECRETS wiring) — without it,
#    encrypted credentials cannot be decrypted and transfers will fail.
# 5. Start the backend. It runs Flyway (no-op on a matching schema) and boots.
# 6. Post-restore validation (below).
```

## Post-restore validation

1. **Audit chain integrity** — the audit log is hash-chained; a partial/corrupt restore breaks it.
   Call the chain-verification path (AuditService.verifyChain per tenant) and confirm it reports
   intact for every tenant. A broken chain means the restore is incomplete — do not resume traffic.
2. **Tenant isolation** — log in as two tenants; confirm each sees only its own data (RLS intact).
3. **Credential decryption** — trigger one test transfer per tenant to confirm the vault keys match.
4. **Row counts** — spot-check tenants/users/transfers against the source system's last-known counts.

## Verification history

The Postgres backup→wipe→restore roundtrip was exercised against a real PostgreSQL 16 instance:
seeded multi-tenant data (tenants + hash-chained audit_events) → `pg_dump -Fc` → **dropped all
tables** → `pg_restore --clean --if-exists` → all rows, tenant names, and audit hashes returned
intact. Re-run this drill at least quarterly and before each major release; record the date and
result here.

| Date | Operator | Scope | Result |
|------|----------|-------|--------|
| 2026-07-09 | Engineering | Postgres logical dump roundtrip (drop→restore) | PASS — all rows/tenants recovered |

## Gaps to close before regulated production

- [ ] Automate `backup.sh` on a schedule with alerting on failure (silent backup failure is the
      classic DR trap).
- [ ] Enable WAL archiving / managed PITR to move RPO from 24h → minutes.
- [ ] Store backups off-host with Object Lock (immutable) + lifecycle/retention policy.
- [ ] Add an automated monthly restore-drill job in a scratch environment (prove backups actually
      restore, not just that they were written).
- [ ] Document and test cross-region failover for the SaaS deployment.

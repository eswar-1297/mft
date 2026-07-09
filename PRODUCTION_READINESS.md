# Production Readiness & Pilot Checklist

This is the honest go-live view for a **regulated pilot**. It states what is built and verified,
what must be done before a customer's data touches the system, and how the SOC 2 / pen-test story
maps to the code. Nothing here is aspirational unless marked ⏳.

## 1. What is built and verified (automated where noted)

| Capability | Status | Verified by |
|---|---|---|
| Multi-tenant isolation (Hibernate `@TenantId` + Postgres RLS) | ✅ | Integration test (`SecurityCoreIntegrationTest`) against real Postgres, as the non-superuser runtime role |
| Tenant-scoped by-id reads (no IDOR via `findById`) | ✅ | Same integration test — see §3; RLS blocks it at the DB too |
| DB-enforced isolation (RLS, forced, non-owner role) | ✅ | Integration test proves raw `findById`/`count` are tenant-scoped |
| Tamper-evident hash-chained audit log | ✅ | Unit tests + integration test (DB tamper → detected) |
| Argon2id credentials, timing-safe login, lockout | ✅ | Unit + manual E2E |
| JWT auth + RBAC | ✅ | Manual E2E |
| MFA (TOTP) enroll/enforce | ✅ | Unit + manual E2E (real computed code) |
| SSO (OIDC) ID-token validation | ✅ | Manual E2E vs Keycloak |
| SFTP transfers + host-key pinning + SHA-256 integrity | ✅ | Manual E2E (real SFTP, MITM rejected) |
| Durable + scheduled workflows (Temporal) | ✅ | Manual E2E (crash-resume, cron fired unattended) |
| PGP encrypt/decrypt (OpenPGP) | ✅ | Unit + `gpg` interop |
| Credential vault (AES-256-GCM), encrypted Temporal payloads | ✅ | Unit + manual E2E (no cleartext in history) |
| S3 cloud connectors | ✅ | Manual E2E vs a second MinIO |
| SIEM export (cursor-based, at-least-once) | ✅ | Manual E2E vs a mock sink |
| Legacy import (MOVEit/GoAnywhere → workflows), XXE-hardened | ✅ | Manual E2E + XXE rejection |
| CI (build, unit + integration tests, compose validate) | ✅ | `.github/workflows/ci.yml` |

## 2. MUST-DO before a paid regulated pilot (blockers)

These are the real gates. Do not put customer data in until these are done.

1. **Rotate all default secrets.** `JWT_SECRET`, `MFT_CRED_KEY` (vault), DB and MinIO credentials,
   the `mft_app` role password, and the bootstrap admin password all ship with dev defaults.
   Generate fresh values (`openssl rand -base64 48` / `-base64 32`). Enforced: under the `prod`
   Spring profile the app **refuses to start** if the JWT secret, vault key, or admin password is
   still the dev default (`SecretsPolicy`).
2. ✅ **Key management seam wired to Vault.** `JWT_SECRET` and `MFT_CRED_KEY` resolve through
   `SecretResolver` (`file:/path`, `env:NAME`, or literal). **Verified end-to-end against a real
   HashiCorp Vault dev server** (2026-07-09): secrets seeded into Vault KV v2 → materialized to
   files (mode 600) → crypto master key decodes to exactly 32 bytes and byte-matches the Vault
   value → consumed via `file:` refs. Tooling + runbook in `deploy/secrets/` (`vault-bootstrap.sh`,
   `materialize-secrets.sh`, `README.md`). In Kubernetes the External Secrets Operator / Secrets
   Store CSI driver mount AWS KMS / Azure Key Vault values at the same paths with zero code change;
   a `kms:` SDK branch is a documented alternative. **To finish for prod:** point at your real
   Vault/KMS and set the rotation schedule (documented in `deploy/secrets/README.md`).
3. ✅ **TLS at ingress.** DONE + verified: `deploy/tls/nginx-tls.conf` terminates TLS 1.2/1.3 with
   HSTS (`max-age=31536000; includeSubDomains`), `X-Content-Type-Options: nosniff`, `X-Frame-Options:
   DENY`, and a HTTP→HTTPS 301 redirect, proxying to the frontend and backend. Wired as the `tls`
   compose profile (`docker compose --profile tls up`; `deploy/tls/gen-cert.sh` for a dev cert).
   Verified in an isolated nginx test: TLS 1.3 handshake → 200, HSTS + nosniff present, :80 → 301,
   TLS 1.1 refused. **For prod:** swap the self-signed cert for a CA-issued one (ACM/Let's Encrypt/PKI).
4. ✅ **Database Row-Level Security (defense-in-depth).** DONE: `FORCE ROW LEVEL SECURITY` is
   enabled on all tenant-scoped tables (migration V10) with a policy keyed on the per-connection
   `app.tenant_id` GUC, which `TenantAwareDataSource` sets from the request tenant on every
   connection borrow (fail-closed when unscoped). Verified: as the non-superuser runtime role, a
   raw `findById` cannot cross tenants and raw `SELECT count(*)` is tenant-scoped
   (`SecurityCoreIntegrationTest`).
5. ✅ **Separate DB role for the app** (not owner/superuser) so RLS is not bypassed. DONE: the app
   runtime connects as the non-superuser `mft_app`; migrations run as the owner/admin. Role is
   provisioned by `db/init/01-app-role.sql` (compose mounts it; CI creates it; DBA/Terraform in
   prod). **Change `mft_app`'s password from the default before production.**
6. **Third-party penetration test** — scope in §4; full readiness package + self-assessment in
   `PENTEST.md`. Still a blocker: the *independent* test itself must be run and Critical/High
   findings remediated + retested before go-live. Everything to hand a firm is ready.
7. ◑ **Backups + DR** — DONE for a limited pilot: `deploy/backup/backup.sh` (Postgres `pg_dump -Fc`
   + object-store mirror + manifest) and `restore.sh`, with documented RPO/RTO and a **verified
   restore roundtrip** (seed → dump → drop all tables → restore → all data recovered, 2026-07-09)
   in `deploy/backup/DR_RUNBOOK.md`. **To finish for regulated prod:** schedule backups with
   failure alerting, enable WAL archiving / managed PITR (24h RPO → minutes), store off-host with
   Object Lock, and add an automated monthly restore drill.

## 3. Security finding closed this cycle (transparency)

Our integration test discovered that Hibernate's `@TenantId` filters **queries** (e.g.
`findByEmail`) but **not** primary-key `findById` loads — so a caller could have fetched another
tenant's record by id (an IDOR-class gap). **Fixed two ways:** (a) all client-facing by-id lookups
use a tenant-scoped HQL query (`findScopedById`); (b) PostgreSQL RLS (§2.4) now blocks cross-tenant
access at the database for the non-superuser runtime role — verified that even a raw `findById`
returns nothing across tenants. This is exactly the class of issue the pilot hardening exists to
catch — and it was caught by an automated test, not in production.

Note the RLS work also surfaced that the default Postgres role is a **superuser, which bypasses
RLS entirely** — which is why the app now connects as a dedicated non-superuser role (§2.5).

## 4. Penetration test scope (proposed)

- **AuthN/AuthZ**: JWT forgery/expiry, RBAC bypass, MFA bypass, OIDC token substitution/audience
  confusion, login brute-force/lockout, account enumeration (timing).
- **Multi-tenancy**: cross-tenant access via every id-bearing endpoint (IDOR), including transfers,
  partners, workflows, runs, connectors, audit.
- **Injection/parsing**: XXE (legacy import), SQLi (JPA/parameterized — verify), SSRF via SFTP/S3
  connector targets and SIEM destination URLs.
- **Crypto**: PGP handling, vault token exposure, JWT secret strength, TLS config.
- **Transfer engine**: SFTP host-key bypass, path traversal in remote paths, oversized-file DoS.
- **Secrets at rest**: confirm no plaintext credentials in DB, logs, or Temporal history.

## 5. SOC 2 (Type II) control mapping — where the evidence lives

| Trust Services Criterion | Control in this system |
|---|---|
| CC6.1 Logical access | JWT + RBAC (`SecurityConfig`, `@PreAuthorize`), Argon2id, MFA, SSO |
| CC6.1 Least privilege | Roles OWNER/ADMIN/OPERATOR/AUDITOR/PARTNER; per-endpoint authorization |
| CC6.6 Encryption in transit | TLS 1.2/1.3 + HSTS at ingress (§2.3, verified); SFTP/PGP for partner exchange |
| CC6.7 Encryption at rest | AES-256-GCM vault; keys via KMS/Vault seam (§2.2, verified); PGP payloads |
| CC7.2 Monitoring | Tamper-evident audit log + SIEM export (Splunk/Sentinel) |
| CC7.3 Anomaly response | ⏳ anomaly detection (roadmap); SIEM alerting today |
| CC8.1 Change management | CI (`ci.yml`), Flyway migrations, code review |
| A1.2 Availability | Durable workflows (Temporal); backups/DR verified (§2.7, `DR_RUNBOOK.md`) |
| PI1 / audit integrity | Hash-chained log with independent `verifyChain()` |

> Full Type II control matrix with per-criterion evidence pointers: **`SOC2_CONTROLS.md`**.
> Pen-test scope, self-assessment, and firm-engagement checklist: **`PENTEST.md`**.

## 6. Deployment hardening checklist

- [ ] All default secrets rotated; master keys from KMS/Vault (seam verified — `deploy/secrets/`)
- [x] TLS 1.2/1.3 + HSTS at ingress (`deploy/tls/`, `tls` compose profile) — swap in a CA cert for prod
- [x] App DB role is non-owner; RLS enabled and forced
- [ ] `BOOTSTRAP_ENABLED=false` after first-run provisioning
- [ ] CORS locked to the real frontend origin(s)
- [ ] Container runs as non-root (already: backend Dockerfile) with read-only FS where possible
- [ ] Resource limits + autoscaling; health probes wired (`/actuator/health`)
- [ ] Centralized structured logging with PII scrubbing; log retention policy
- [x] Postgres backup/restore scripts + DR runbook; restore roundtrip verified (`deploy/backup/`) — add scheduling + PITR + off-host Object Lock for prod
- [ ] Dependency & container scanning in CI (add Trivy/Dependabot) ⏳
- [ ] Rate limiting / WAF at the edge
- [ ] Incident response runbook + on-call

## 7. Known gaps (roadmap, not blockers for a limited pilot)

FTPS + AS2/MDN protocols; Azure/Drive/SharePoint/Box connectors; byte-level checkpoint-resume;
public/private-key PGP; SAML + JIT provisioning; anomaly detection; regulator-report PDF; partner
portal; AI Copilot. See `ROADMAP.md`.

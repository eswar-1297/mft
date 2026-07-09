# CloudFuze MFT

A **real, secure Managed File Transfer product** — not a demo mock. Multi-tenant, deployable as
cloud SaaS or on-premises, built to beat MOVEit, GoAnywhere, Sterling, Kiteworks, and the modern
upstarts on the one thing that matters most in this market: **trust**.

> Status: **v0.15 — + AI Copilot.**
> Adds the AI Copilot: **natural-language workflow drafting** (NL → pipeline steps + suggested
> cron; advisory — you review before saving) and **plain-English audit Q&A** grounded in the
> tamper-evident log with cited evidence. Runs a deterministic engine with no key; uses **Claude
> (Anthropic Java SDK)** when `ANTHROPIC_API_KEY` is set. Verified E2E (rule-based path). Prior:
> Master keys (JWT signing secret, vault key) no longer need to live as plaintext env: they
> resolve through `SecretResolver` (`file:` / `env:` references), so KMS/Vault values injected as
> mounted secret files work with zero code. A `prod`-profile guardrail refuses to boot on
> dev-default keys. Verified — app boots in `prod` with file-sourced keys, JWT + vault crypto work
> from them, and dev defaults are rejected. Prior:
> Tenant isolation is now enforced at the **database**: forced RLS on all tenant-scoped tables
> keyed on a per-connection `app.tenant_id` GUC, with the app running as a dedicated non-superuser
> role (migrations run as the owner). Verified — as the runtime role, even a raw `findById` cannot
> cross tenants, and the full app (bootstrap, login, workflows, audit) works unchanged. Prior:
> Adds **CI** (`.github/workflows/ci.yml`: backend build + unit **and integration** tests against a
> real Postgres, frontend build, compose validation), **Testcontainers-free integration tests**
> proving multi-tenant isolation and audit-tamper detection against real Postgres (which caught &
> fixed a real IDOR-class gap — `findById` isn't tenant-filtered, now uses scoped queries), and
> [PRODUCTION_READINESS.md](./PRODUCTION_READINESS.md) (pen-test scope, SOC 2 mapping, KMS, go-live
> blockers). Prior: **legacy import**; **SIEM export**; **scheduled workflows**; **S3 connectors**;
> **OIDC SSO + MFA**. Earlier:
> Adds **legacy import**: upload a MOVEit/GoAnywhere config export and each job is rebuilt as a
> real CloudFuze workflow, with a "N imported, M need review" summary and per-job wire-up notes
> (XXE-hardened parser; verified — a 5-task file produced 5 mapped workflows, malicious XXE
> rejected). Prior: **SIEM export** (cursor-based, verified vs a mock sink); **scheduled
> workflows** (Temporal cron); **S3 cloud connectors**; **OIDC SSO** + **MFA (TOTP)**. Earlier:
> The security core (multi-tenant isolation, Argon2id credentials, JWT auth, RBAC, tamper-evident
> audit log), a real streaming **SFTP transfer engine** with SHA-256 integrity, **durable Temporal
> workflows** (retry with backoff, resume-after-crash), a **multi-step pipeline engine**
> (Pickup → PGP Encrypt → Validate → Send → Archive → Notify with real, `gpg`-interoperable
> OpenPGP), an **AES-256-GCM credential vault**, a **React web console**, and **security hardening**
> (SFTP host-key pinning, encrypted Temporal payloads, login brute-force lockout) are built and
> verified end-to-end against live Postgres/MinIO/SFTP/Temporal.
> Scheduling, AS2/FTPS, cloud connectors, public-key PGP, and SSO are on the [roadmap](./ROADMAP.md).

## Why this exists

The incumbents were destroyed by breaches (MOVEit 2023: 2,500+ orgs; GoAnywhere 2023). Selling MFT
to banks and hospitals is selling *trust*. So this codebase treats security as the product, not a
feature — starting with an audit log a regulator can independently re-verify. See [SECURITY.md](./SECURITY.md).

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Java 21 + Spring Boot 3.3 |
| Security | Spring Security, JWT (HS256), Argon2id password hashing |
| Data | PostgreSQL 16, Flyway migrations, Hibernate discriminator multi-tenancy |
| Object storage | S3 / MinIO (on-prem) |
| Frontend | React + TypeScript + Vite + Tailwind *(coming)* |
| Deploy | Docker / Docker Compose; single container for on-prem, managed services for SaaS |

## Quick start

### Option A — Docker Compose (everything)
```bash
docker compose up --build
# Web console  http://localhost:3000   (log in with the bootstrap creds below)
# API          http://localhost:8080
# Temporal UI  http://localhost:8233
# MinIO console http://localhost:9001
```

### Frontend dev server (hot reload against the backend)
```bash
docker compose up -d postgres minio temporal backend
cd frontend && npm install && npm run dev   # http://localhost:5173 (proxies /api to :8080)
```

### Option B — run the backend locally (needs JDK 21 + a Postgres)
```bash
# start just the database
docker compose up -d postgres
cd backend
DB_URL=jdbc:postgresql://localhost:5432/mft mvn spring-boot:run
```

### First login
On first start the app provisions one tenant and one OWNER account (configurable — see
`.env.example`). Defaults:

```bash
curl -s http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"tenantSlug":"demo","email":"admin@cloudfuze.com","password":"ChangeMe!2026"}'
```

Use the returned `accessToken` as `Authorization: Bearer <token>` for other endpoints:

```bash
# who am I
curl http://localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"
# the immutable audit log
curl http://localhost:8080/api/audit -H "Authorization: Bearer $TOKEN"
# independently verify the audit chain is intact
curl http://localhost:8080/api/audit/verify -H "Authorization: Bearer $TOKEN"
```

### Move a file (transfer engine)
Transfers run as **durable Temporal workflows**: the POST returns `202` immediately with the
transfer in `PENDING`, execution happens on a worker with automatic retry, and it completes even
if the API process restarts. Poll `GET /api/transfers/{id}` for status.

```bash
# 1. upload a file into the object store -> returns a storage key + sha256
curl http://localhost:8080/api/files -H "Authorization: Bearer $TOKEN" -F "file=@./mydata.csv"

# 2. push a stored object out to a partner SFTP server (starts a durable workflow, returns 202)
curl http://localhost:8080/api/transfers/sftp-push -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{
    "storageKey":"<key from step 1>",
    "sftp":{"host":"partner.example.com","port":22,"username":"foo","password":"..."},
    "remotePath":"/upload/mydata.csv"}'

# 3. pull a partner's file into the object store
curl http://localhost:8080/api/transfers/sftp-pull -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{
    "sftp":{"host":"partner.example.com","port":22,"username":"foo","password":"..."},
    "remotePath":"/upload/incoming.csv"}'

# poll status; every transfer is tracked and audited
curl http://localhost:8080/api/transfers/<id> -H "Authorization: Bearer $TOKEN"
```

The Temporal Web UI (workflow history, retries) is at http://localhost:8233 when running via
`docker compose`.

## Documentation

- [ARCHITECTURE.md](./ARCHITECTURE.md) — how the system is structured and why
- [SECURITY.md](./SECURITY.md) — the security model and the road to SOC 2 / ISO 27001
- [ROADMAP.md](./ROADMAP.md) — phased plan from this foundation to a sellable product

## Configuration

All configuration is environment-variable driven (12-factor). See [`.env.example`](./.env.example).
**Before any real deployment**, override `JWT_SECRET` and the bootstrap admin password.

## Tests
```bash
cd backend && mvn test            # unit tests (no DB needed)

# Integration tests (real Postgres) — the security-core proofs. Provide a DB via IT_DB_URL:
docker run -d --name it-pg -e POSTGRES_DB=mft -e POSTGRES_USER=mft -e POSTGRES_PASSWORD=mft -p 5432:5432 postgres:16-alpine
cd backend && IT_DB_URL=jdbc:postgresql://localhost:5432/mft mvn verify
```
CI runs both on every push (`.github/workflows/ci.yml`). For the pilot go-live view, see
[PRODUCTION_READINESS.md](./PRODUCTION_READINESS.md).

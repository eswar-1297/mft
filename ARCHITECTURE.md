# Architecture

## Shape

A modular monolith (Spring Boot) fronting PostgreSQL and S3-compatible object storage, with a
React SPA. A monolith — not microservices — is the right call for a small team shipping fast; the
package boundaries below are drawn so pieces (e.g. the transfer engine) can be split out later if
scale demands it.

```
                 ┌─────────────────────────────┐
   Browser  ───► │  React SPA (Vite/TS/Tailwind)│   (roadmap: wire onto real APIs)
                 └──────────────┬──────────────┘
                                │ HTTPS + JWT
                 ┌──────────────▼──────────────┐
                 │      Spring Boot backend     │
                 │  auth · tenant · audit ...   │
                 │  transfer engine (roadmap)   │
                 │  workflows (Temporal, r/map) │
                 └───┬───────────────┬──────────┘
        ┌────────────▼───┐     ┌─────▼─────────┐
        │  PostgreSQL 16 │     │  S3 / MinIO   │   file payloads
        │  (Flyway-owned)│     └───────────────┘
        └────────────────┘
   Partners (banks, insurers) ⇄ SFTP/FTPS/AS2/HTTPS (roadmap: transfer engine)
```

## Backend packages (`com.cloudfuze.mft`)

| Package | Responsibility |
|---|---|
| `tenantmodel` | The global `Tenant` registry (the one non-tenant-scoped entity). |
| `tenant` | `TenantContext` (per-request tenant) + Hibernate `TenantResolver`. |
| `auth` | Users, roles, Argon2id hashing, JWT issue/verify, the login flow, RBAC. |
| `audit` | The tamper-evident hash-chained audit log — append + verify. |
| `config` | Security config, JPA/multi-tenancy wiring, first-run bootstrap. |
| `common` | Shared errors, health. |

## Key decisions

- **Multi-tenancy = discriminator (shared schema, `tenant_id` column).** Cheapest to operate for
  SaaS, simplest to reason about, and enforced automatically by Hibernate `@TenantId`. On-prem
  installs simply run with a single tenant. Schema-per-tenant can come later for customers who
  contractually require physical separation.
- **Flyway owns the schema** (`ddl-auto: none`). The database is migrated deterministically and
  Hibernate never emits or mutates DDL — the production-safe posture.
- **Stateless JWT auth.** No server sessions to scale or fixate; horizontal scaling is trivial.
- **Audit appends serialized per tenant** via a pessimistic lock on a per-tenant "chain head"
  row. A hash chain *requires* serial appends; this guarantees it without a global bottleneck.
- **Config is 100% environment-driven** (12-factor) so the same artifact runs in SaaS and on-prem.

## Deployment models

- **SaaS (multi-tenant):** one backend fleet + managed Postgres (RDS) + S3. Tenants isolated by
  discriminator + (roadmap) row-level security.
- **On-prem (single-tenant):** `docker compose up` on the customer's hardware; Postgres + MinIO
  bundled. Single static-ish container, no external dependencies, air-gap friendly.

## Data flow: a login (implemented today)
1. `POST /api/auth/login` with `{tenantSlug, email, password}`.
2. Resolve tenant by slug (global lookup), bind `TenantContext`.
3. Look up the user *within that tenant*, verify the Argon2id hash (timing-safe).
4. Issue a JWT carrying `sub`, `tenant`, `role`. Audit the login (success or failure).
5. Subsequent requests: `JwtAuthFilter` verifies the token, rebinds tenant + authorities.

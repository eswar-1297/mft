# Roadmap — from foundation to sellable product

Honest framing: a real MFT that banks trust is a multi-month build with real engineers, a
pen-test, and a SOC 2 audit. This roadmap sequences that work so each phase is demoable and
independently valuable. Competitor gaps (from the product brief) are mapped to the phase that
closes them.

## ✅ Phase 0 — Secure foundation (done, v0.1)
- Monorepo, Docker Compose, docs.
- Multi-tenant Postgres + Flyway; Hibernate `@TenantId` isolation.
- Argon2id credentials, JWT auth, RBAC, timing-safe login.
- Tamper-evident hash-chained audit log + verifier (tested).
- First-run bootstrap (tenant + owner).

## Phase 1 — Transfer engine *(in progress)*
Real file movement, the heart of the product.
- ✅ **SFTP client** transfers (pull partner→store, push store→partner) via Apache MINA SSHD.
- ✅ Object store (S3/MinIO) ingestion with **SHA-256 integrity verification** end-to-end
  (verified: upload → SFTP push → SFTP pull round-trips byte-identical).
- ✅ Transfer lifecycle + status + bounded retry, every step written to the audit log.
- ✅ HTTPS/REST file ingestion (`POST /api/files`).
- ⏳ **FTPS** and an inbound **SFTP server** (partners push to us).
- ⏳ **PGP** encrypt/decrypt (Bouncy Castle) in the transfer pipeline.
- ⏳ **AS2** with signed/encrypted payloads and MDN receipts (B2B/EDI — beats the upstarts).
- ⏳ Partner credential vault (envelope-encrypted) — today SFTP creds are per-request, not stored.
- ⏳ Checkpoint-resume (resume, not restart) — lands with the workflow engine (Phase 2).
- *Closes:* Couchdrop/Files.com enterprise-depth gap; Kiteworks B2B-automation gap.

## Phase 2 — Durable workflows (Temporal) *(mostly done)*
- ✅ Transfers execute as **durable Temporal workflows** — async start, poll for status.
- ✅ **Workflow-managed retry with exponential backoff** (verified: 3 attempts, full audit trail).
- ✅ **Survives process crashes** (verified: killed the app mid-transfer → workflow resumed on
  restart and ran to completion, with no resubmission — the job is never lost).
- ✅ **Multi-step pipeline**: Pickup → PGP Encrypt/Decrypt → Validate → Send → Archive → Notify,
  each a durable, retried, audited step, run as a Temporal workflow. **Real OpenPGP** (Bouncy
  Castle) — verified interoperable: standard `gpg` decrypts the pipeline's output to the exact
  original bytes. Failure stops at the offending step with a clear message (verified).
- ✅ **Workflow builder UI**: compose from a step palette, configure, reorder, run, and watch
  per-step execution in the run history (verified + screenshotted).
- ✅ **Scheduled workflows (Temporal cron)** — attach a cron to a saved workflow; it fires
  automatically, creating a fresh run each time. Enable/disable from API + UI. Verified: an
  every-minute schedule produced runs one minute apart with no manual trigger; unschedule stops it.
- ⏳ Byte-level checkpoint-resume (resume a partial file, not just re-run the attempt).
- ⏳ Public/private-key PGP (partner key management); today PGP is passphrase-based.
- ⏳ Escalation policies; smart failure diagnosis ("partner endpoint down, not your file").
- *Closes:* the industry ~4-hour MTTR; reliability + multi-step automation are provable, not marketing.

## Phase 3 — Connectors + partners *(in progress)*
- ✅ **Connector abstraction + S3 connector** — external S3-compatible endpoints (AWS S3, MinIO,
  Wasabi, …) as first-class transfer **sources and destinations**, with a vault-encrypted secret
  and a "test connection" round-trip. Wired into the pipeline (PICKUP-from / SEND-to connector).
  Verified E2E against a separate external MinIO: send lands byte-identical, pickup pulls correctly.
- ✅ Partner profiles + credential vault + host-key pinning (done earlier).
- ⏳ Azure Blob, Google Drive, SharePoint, Box connectors (each needs its SDK + OAuth; the
  abstraction is in place). Database connectors.
- ⏳ Certificate management, partner health scoring.
- ⏳ Branded partner portal (external self-service drop/pull) + self-onboarding.
- *Closes:* Sterling/Axway weak cloud connectivity; MOVEit fragmented add-ons.

## Phase 4 — Enterprise UI *(mostly done)*
- ✅ React + TS + Vite + Tailwind console in CloudFuze brand (Poppins, light/dark).
- ✅ Wired to real APIs: JWT login, live dashboard (KPIs + MTTR from real transfers), transfers
  (start durable pull/push, poll status, detail drawer), ad-hoc upload, audit log with live
  chain-verify, trust center. Verified end-to-end + screenshotted.
- ✅ **Partners** screen + real backend (connection profile + AES-256-GCM **credential vault**).
- ✅ **Onboarding wizard**: connect a partner → stage a file → run a real durable transfer, with a
  live **elapsed-time chip** — drives real APIs end to end (verified).
- ✅ Served via nginx in `docker compose` (same-origin API proxy).
- ✅ Visual **workflow builder** — step palette + configure + reorder + run + per-step run history
  (built on the real pipeline engine; see Phase 2).
- ⏳ Partner portal (external self-service view); sales "demo-mode" scripted controls.
- *Closes:* MOVEit/Sterling dated UX and slow deployment.

## Security hardening *(done, v0.6)*
- ✅ **SFTP host-key pinning** — reject connections whose server key doesn't match the pinned
  fingerprint (verified: key change → rejected).
- ✅ **Encrypted Temporal payloads** — AES-256-GCM codec; workflow history holds only ciphertext
  (verified: no cleartext secrets in history).
- ✅ **Login brute-force lockout** — 5 failures → 15-min lockout (verified).
- ⏳ AS2 + MDN, FTPS (new protocols — feature work, tracked with connectors).
- ⏳ Public/private-key PGP (task #9).

## Phase 5 — Trust, compliance, SSO *(in progress)*
- ✅ **SSO (OIDC)** — validate IdP ID token via JWKS (issuer/audience/signature), map to user,
  issue session JWT. Verified end-to-end against Keycloak.
- ✅ **MFA (TOTP)** — enroll/confirm/enforce, authenticator-app compatible, vault-encrypted secret.
- ⏳ SAML (additive to OIDC); JIT user provisioning per tenant; SSO browser redirect glue in the SPA.
- ✅ **KMS/Vault key management** — master keys (JWT, crypto vault, DB) resolve through
  `SecretResolver` (`file:`/`env:`) sourced from HashiCorp Vault / AWS KMS / Azure Key Vault.
  Verified end-to-end against a real Vault dev server (seed → materialize → 32-byte key matches).
  Tooling + rotation runbook in `deploy/secrets/`.
- ✅ **SIEM export** — cursor-based forwarder ships audit events to HTTP/HEC or syslog-CEF,
  at-least-once (advances cursor only on success; verified no loss and no duplicates). Per-tenant
  config + live status in the Trust Center. Closes the "63% of MFT lacks SIEM" gap.
- ⏳ Anomaly detection ("unusual bulk download blocked").
- ⏳ One-click regulator report (PDF) + chain-of-custody views (built on the audit log).
- ⏳ Trust Center page enrichment (certs, architecture, zero-breach posture).
- *Closes:* the 63%-lack-SIEM gap; MOVEit's shattered trust.

## Phase 6 — Differentiators *(in progress)*
- ✅ **Legacy import**: parse MOVEit/GoAnywhere config exports → real CloudFuze workflows, with a
  "N imported, M need review" summary and per-job notes on what to wire up. XXE-hardened parser.
  Verified: a 5-task MOVEit file produced 5 correctly-mapped workflows; malicious XXE file rejected.
  (Sterling and full-fidelity vendor formats are incremental — the parser abstraction is in place.)
- ✅ **AI Copilot**: natural-language workflow creation (NL → pipeline steps + suggested cron,
  advisory/non-autonomous) and plain-English audit answers grounded in the tamper-evident log with
  cited evidence. Deterministic engine (works with no key) + real Claude via the Anthropic Java SDK
  behind a seam when a key is set. Verified E2E. ⏳ anomaly explanations still to come.
- ✅ Migration + MFT in one vendor (CloudFuze's unique angle) — the import flow embodies this.

## Cross-cutting (continuous)
- ✅ **CI** (GitHub Actions): backend build + unit **and integration** tests (real Postgres),
  frontend typecheck+build, docker-compose validation.
- ✅ **Integration tests** proving multi-tenant isolation + audit-tamper detection against real
  Postgres (caught & fixed an IDOR-class `findById` gap — now tenant-scoped reads).
- ✅ **PRODUCTION_READINESS.md**: pen-test scope, SOC 2 control mapping, KMS, go-live blockers.
- ✅ **Pilot gates (non-code)**: TLS 1.2/1.3 + HSTS at ingress (`deploy/tls/`, verified);
  backup/restore + DR runbook with verified restore roundtrip (`deploy/backup/`); KMS/Vault secret
  wiring verified (`deploy/secrets/`); pen-test readiness package (`PENTEST.md`); Type II control
  matrix with evidence pointers (`SOC2_CONTROLS.md`).
- ⏳ SAST/DAST + dependency/container scanning (Trivy/Dependabot) in CI.
- ⏳ Observability (metrics, tracing, structured logs).
- ⏳ Scheduled external penetration tests; SOC 2 Type II + ISO 27001 evidence collection.
- ✅ Postgres Row-Level Security (DB-enforced tenant isolation, defense-in-depth) — forced RLS +
  per-connection `app.tenant_id` GUC + non-superuser runtime role; verified a raw `findById`
  cannot cross tenants.

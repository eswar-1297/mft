# CloudFuze MFT — Manual Test Guide

A step-by-step walkthrough to exercise **every feature** yourself. Each test case says what it
proves, what to click/type, and exactly what you should see. Work top to bottom — later tests reuse
data created in earlier ones.

> Legend: 🟢 = works fully offline (no external setup) · 🟡 = needs a helper (a throwaway SFTP or S3
> server — setup provided in §2) · 🔵 = needs an external service (Keycloak, real Claude key) and is
> optional for a first pass.

---

## 1. Start the stack & sign in

### Start everything
```bash
cd cloudfuze-mft
docker compose up -d --build
```
Wait until all services are healthy:
```bash
docker compose ps
```
You should see `postgres`, `minio`, `temporal`, `backend`, `frontend` all `Up` (postgres/minio show
`healthy`).

### URLs
| What | URL |
|------|-----|
| **The app (UI)** | http://localhost:3000 |
| Backend API | http://localhost:8080/api |
| Backend health | http://localhost:8080/api/health |
| Temporal Web UI | http://localhost:8233 |
| MinIO console | http://localhost:9001 (login `mftminio` / `mftminio123`) |

### Login credentials (first-run bootstrap)
| Field | Value |
|-------|-------|
| **Organization / Tenant slug** | `demo` |
| **Email** | `admin@cloudfuze.com` |
| **Password** | `ChangeMe!2026` |

### TC-01 🟢 — Login & session
1. Open http://localhost:3000 → you're redirected to **/login**.
2. Enter tenant `demo`, email `admin@cloudfuze.com`, password `ChangeMe!2026`, click **Sign in**.
3. **Expect:** you land on the **Dashboard**; the left nav shows Dashboard, Get started, Workflows,
   Transfers, Partners, Connectors, Migrate/Import, Ad-hoc Send, Audit, Security, Trust Center.
4. Refresh the page → you stay logged in (JWT persisted).

### TC-02 🟢 — Wrong password is rejected + lockout
1. Log out (top-right menu). On /login, enter the right tenant/email but a **wrong password** 5×.
2. **Expect:** each attempt fails with an error; after 5 failures within 15 min the account is
   **locked** and even the correct password is refused for the lockout window. This proves
   brute-force protection (`LoginThrottle`).
3. Wait it out or `docker compose restart backend` to clear the in-memory throttle, then log back in.

---

## 2. One-time helpers for transfer tests (🟡 cases)

The transfer engine moves real files, so a couple of tests need a partner endpoint. Spin up
throwaway servers on the same Docker network.

### A test SFTP server
```bash
docker run -d --name test-sftp --network cloudfuze-mft_default \
  -p 2222:22 atmoz/sftp:alpine foo:pass:::upload
```
- Host (from the backend's view): `test-sftp`, Port `22`, User `foo`, Password `pass`.
- Writable directory: `/upload`. (From your laptop it's also on `localhost:2222`.)

### A test file inside that SFTP server (for pull tests)
```bash
echo "hello from partner $(date)" > sample.txt
docker cp sample.txt test-sftp:/upload/sample.txt
```

### A second S3 bucket (for the connector test) — reuse the built-in MinIO
The bundled MinIO already serves S3. You'll point a connector at it in TC-13.

> Clean up when done: `docker rm -f test-sftp`.

---

## 3. Ad-hoc send & transfers

### TC-03 🟢 — Upload a file into secure storage
1. Nav → **Ad-hoc Send**.
2. Click the drop zone, pick any small file, click **Upload**.
3. **Expect:** the file appears under "Uploaded (this session)" with a **storage key** (e.g.
   `demo/<uuid>/filename`). Click **Copy** to copy the key — you'll need it in TC-04.
4. (Optional) Open MinIO console (http://localhost:9001) → bucket `mft-files` → confirm the object
   is there.

### TC-04 🟡 — Push a stored file to a partner over SFTP (durable transfer)
*Preconditions: TC-03 (a storage key) + §2 (test-sftp running).*
1. Nav → **Transfers** → click **Push to partner**.
2. Fill in: **Storage key** = the key from TC-03; **Remote path** = `/upload/pushed.txt`;
   **Partner SFTP host** = `test-sftp`, **Port** `22`, **Username** `foo`, **Password** `pass`.
3. Click the start button.
4. **Expect:** a new row appears in the transfers table; status moves `PENDING → RUNNING →
   SUCCEEDED` (click **Refresh** to poll). This is a real Temporal workflow.
5. Verify the file actually landed:
   ```bash
   docker exec test-sftp ls -l /upload/
   ```
   You should see `pushed.txt`.

### TC-05 🟡 — Pull a partner's file into storage
*Preconditions: §2 created `/upload/sample.txt`.*
1. Transfers → **Pull from partner**.
2. **Remote path** = `/upload/sample.txt`; same SFTP host/user/pass as TC-04.
3. Start → **Expect:** status reaches `SUCCEEDED`; a new object appears in `mft-files` (check MinIO
   console). This proves inbound transfer + SHA-256 integrity verification.

### TC-06 🟡 — Host-key pinning rejects a MITM (security)
*This proves we detect a swapped server key.*
1. Pin the real key first: Partners → add a partner (TC-11) pointing at `test-sftp`, then use
   **Pin host key**. (Or run a transfer once to observe the fingerprint in the audit log.)
2. Recreate the SFTP server so it generates a **new** host key:
   `docker rm -f test-sftp` then re-run the §2 command.
3. Run a transfer to that partner again.
4. **Expect:** the transfer **fails** with a host-key mismatch error rather than silently
   connecting. That's the anti-MITM control working.

### TC-07 🟢 — Transfer detail & audit trail
1. Transfers → click any row.
2. **Expect:** a detail drawer with direction, status, filename, timestamps, and per-step history.
   Every state change here also appears in the Audit log (TC-16).

---

## 4. Durable & scheduled workflows

### TC-08 🟢 — Build a multi-step pipeline
1. Nav → **Workflows** → **New workflow**.
2. Name it `Nightly claims`. From the step palette add, in order: **Pickup → PGP Encrypt →
   Validate → Send → Archive → Notify** (reorder with the up/down arrows; remove with ✕).
3. Configure each step's fields as prompted, then save.
4. **Expect:** the workflow appears in the list with its step count.

### TC-09 🟢 — Run a workflow on demand
1. On the workflow row, click **Run**.
2. Scroll to **Recent runs** → **Expect:** a new run appears and progresses per-step; a failure (if
   any step is misconfigured) stops at the offending step with a clear message. Each step is
   durable and audited.

### TC-10 🟢 — Schedule a workflow (cron) and watch it fire unattended
1. On the workflow row click the **clock / Schedule** button.
2. Enter a cron of `* * * * *` (every minute) and save. The row now shows the schedule chip.
3. Wait ~2 minutes, watch **Recent runs**.
4. **Expect:** new runs appear ~1 minute apart with **no manual trigger**. Remove the schedule to
   stop it; confirm no new runs appear afterward. (Cross-check in the Temporal UI at :8233.)

---

## 5. Partners & connectors

### TC-11 🟢 — Add a partner with an encrypted credential
1. Nav → **Partners** → **Add partner**.
2. Name `HDFC Bank`, SFTP host `test-sftp`, port `22`, user `foo`, password `pass`.
3. Save → **Expect:** the partner appears. The password is stored **encrypted** (AES-256-GCM), never
   shown back in plaintext.
4. (Optional) Confirm at the DB level nothing is cleartext:
   ```bash
   docker exec cloudfuze-mft-postgres-1 psql -U mft -d mft -c \
     "select name, left(secret_enc,24) from partners;"
   ```
   The `secret_enc` column is ciphertext, not `pass`.

### TC-12 🟢 — Delete a partner
1. Click the trash icon on a partner row → confirm.
2. **Expect:** it disappears from the list.

### TC-13 🟡 — Add & test an S3 connector
1. Nav → **Connectors** → **Add connector**.
2. Name `Demo S3`, Type **Amazon S3 / compatible**, **Endpoint** `http://minio:9000`, Region
   `us-east-1`, Bucket `mft-files`, Access key `mftminio`, Secret key `mftminio123`, tick
   path-style. Save.
3. On the connector row click **Test connection**.
4. **Expect:** a green success (a real round-trip to MinIO). Azure/Drive/SharePoint/Box show as
   "coming soon" — that's expected.

---

## 6. Migration / legacy import

### TC-14 🟢 — Import a MOVEit config into real workflows
*A sample file ships at `examples/moveit-export-sample.xml`.*
1. Nav → **Migrate / Import** → drop `examples/moveit-export-sample.xml` → **Import**.
2. **Expect:** a summary like "5 imported, N need review", each mapped to a real workflow with notes
   on what to wire up. Click through to **Workflows** and confirm they're there.

### TC-15 🟢 — Malicious XML is rejected (security)
1. Create a small XXE probe file:
   ```bash
   cat > xxe.xml <<'EOF'
   <?xml version="1.0"?><!DOCTYPE t [<!ENTITY x SYSTEM "file:///etc/passwd">]><tasks>&x;</tasks>
   EOF
   ```
2. Import `xxe.xml`.
3. **Expect:** the import is **rejected / the entity is not expanded** — no file contents leak. This
   proves the parser is XXE-hardened.

---

## 7. Audit & compliance

### TC-16 🟢 — Audit log & filtering
1. Nav → **Audit & Compliance**.
2. **Expect:** a chronological list of events (`user.login`, `transfer.started`,
   `transfer.completed` with filename/dest, `partner.created`, etc.). Use the filter box
   (e.g. `transfer.completed`) to narrow it.

### TC-17 🟢 — Verify tamper-evident chain integrity
1. On the Audit page click **Verify chain integrity**.
2. **Expect:** a green "chain intact" result — every event's hash links to the previous one.

### TC-18 🟢 — Prove tamper detection (advanced, optional)
1. Directly mutate one audit row in the DB:
   ```bash
   docker exec cloudfuze-mft-postgres-1 psql -U mft -d mft -c \
     "update audit_events set detail = detail || ' (tampered)' where seq = 2;"
   ```
2. Click **Verify chain integrity** again.
3. **Expect:** verification now **fails**, pinpointing the break. This is the core compliance
   guarantee — records can't be altered undetectably. (Restore with `docker compose down -v && up`
   if you want a clean chain again.)

---

## 8. Security — MFA

### TC-19 🟢 — Enroll & enforce TOTP MFA
1. Nav → **Security** → **Enable MFA**.
2. **Expect:** a QR code + secret. Scan it with Google Authenticator / Authy (or any TOTP app).
3. Enter the current 6-digit code → **Confirm**. Status flips to **Enabled**.
4. Log out and back in → **Expect:** login now also asks for the **OTP**; the correct code lets you
   in, a wrong/expired one is refused.
5. (Reset) On Security, disable MFA with a valid code if you want to skip OTP for later tests.

---

## 9. Trust Center — SIEM export

### TC-20 🟡 — Stream audit events to a SIEM sink
1. Start a mock HTTP sink to receive events:
   ```bash
   docker run -d --name siem-sink --network cloudfuze-mft_default \
     -p 9099:80 kennethreitz/httpbin
   ```
2. Nav → **Trust Center** → SIEM section. Target `http://siem-sink:80/post`, leave token blank,
   click **Enable streaming**.
3. Generate an event (e.g. run a transfer or log out/in).
4. **Expect:** the Trust Center shows streaming active with an advancing cursor/last-sent marker.
   The forwarder is at-least-once (cursor only advances on success). Clean up: `docker rm -f siem-sink`.

---

## 10. AI Copilot

### TC-21 🟢 — Draft a workflow from natural language (rule-based, no key needed)
1. Open the **Copilot** panel (docked, bottom-right of the app).
2. Type: *"Every night pull invoices from our partner over SFTP, PGP-encrypt them, and archive."*
3. **Expect:** Copilot proposes a pipeline (Pickup → PGP → … → Archive) plus a suggested cron. It's
   **advisory** — you choose whether to create it. Works even without an API key (deterministic
   engine).

### TC-22 🟢 — Ask an audit question grounded in the log
1. In Copilot ask: *"Did the transfer of pushed.txt succeed today?"*
2. **Expect:** a plain-English answer grounded in the audit log, citing the relevant event(s). If
   nothing matches, it says so rather than inventing an answer.

### TC-23 🔵 — Real Claude (optional)
1. Set `ANTHROPIC_API_KEY` in your environment and restart the backend
   (`docker compose up -d backend` with the key passed through).
2. Repeat TC-21/22 → answers are richer/more fluent. Without the key, the rule-based engine is used
   — both are valid.

---

## 11. Access control (RBAC) & multi-tenant isolation

### TC-24 🟢 — Role restrictions (optional, needs a second user)
Roles: OWNER/ADMIN can create/delete; OPERATOR can run/transfer; AUDITOR is read-only. If you create
an AUDITOR user, confirm they **cannot** create partners/workflows (the API returns 403 and the UI
hides/disables those actions).

### TC-25 🟢 — Tenant isolation is enforced at the database
This is covered by an automated integration test, but to see it yourself: a user in tenant `demo`
can never load another tenant's transfer/partner/workflow by ID — both the app (`findScopedById`)
and Postgres Row-Level Security block it. Evidence: `SecurityCoreIntegrationTest`.

---

## 12. TLS at ingress (optional)

### TC-26 🟢 — Serve the app over HTTPS
1. Generate a dev cert: `bash deploy/tls/gen-cert.sh`
2. Start with the TLS profile: `docker compose --profile tls up -d`
3. Visit **https://localhost** (accept the self-signed warning).
4. **Expect:** the app loads over TLS 1.3; visiting **http://localhost** 301-redirects to HTTPS;
   the response carries HSTS + security headers. (Prod uses a CA-issued cert instead.)

---

## 13. Backup & restore (optional)

### TC-27 🟢 — Backup → restore roundtrip
1. Take a backup: `bash deploy/backup/backup.sh` (writes to `./backups/<timestamp>/`).
2. Restore it into a clean DB per `deploy/backup/DR_RUNBOOK.md`.
3. **Expect:** all tenants, users, transfers, and the audit log come back intact; chain verify
   (TC-17) still passes. A verified roundtrip is recorded in the runbook.

---

## Quick API smoke test (optional, no UI)

```bash
# Login → capture JWT (the field is "accessToken")
TOKEN=$(curl -s http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"tenantSlug":"demo","email":"admin@cloudfuze.com","password":"ChangeMe!2026"}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")

# Who am I
curl -s http://localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"

# List transfers / partners / workflows / audit
curl -s http://localhost:8080/api/transfers -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8080/api/audit -H "Authorization: Bearer $TOKEN"
curl -s "http://localhost:8080/api/audit/verify" -H "Authorization: Bearer $TOKEN"
```

---

## Coverage map (feature → test case)

| Feature | Test case(s) |
|---------|--------------|
| Auth, session, brute-force lockout | TC-01, TC-02 |
| Ad-hoc upload to object store | TC-03 |
| SFTP push/pull durable transfers | TC-04, TC-05 |
| SHA-256 integrity | TC-05 |
| Host-key pinning (anti-MITM) | TC-06 |
| Transfer detail / lifecycle | TC-07 |
| Multi-step pipeline (PGP, validate, archive…) | TC-08, TC-09 |
| Scheduled (cron) workflows | TC-10 |
| Partners + encrypted credential vault | TC-11, TC-12 |
| Cloud (S3) connectors + test connection | TC-13 |
| Legacy MOVEit/GoAnywhere import | TC-14 |
| XXE hardening | TC-15 |
| Audit log + filtering | TC-16 |
| Tamper-evident chain + tamper detection | TC-17, TC-18 |
| MFA (TOTP) | TC-19 |
| SIEM export | TC-20 |
| AI Copilot (draft + audit Q&A) | TC-21, TC-22, TC-23 |
| RBAC | TC-24 |
| Multi-tenant isolation (RLS) | TC-25 |
| TLS at ingress | TC-26 |
| Backup / DR | TC-27 |

---

## Troubleshooting

- **Backend not up yet:** `docker compose logs -f backend` — it waits for Postgres/MinIO to be
  healthy, runs Flyway migrations, then serves. First boot after a build takes ~30–60s.
- **A transfer stays PENDING:** check `docker compose logs -f backend` and the Temporal UI (:8233).
  Usually the SFTP host/credentials are wrong or `test-sftp` isn't on the same network.
- **Reset everything to a clean slate:** `docker compose down -v && docker compose up -d --build`
  (the `-v` wipes the DB and object store, restoring the first-run bootstrap).
- **Container name for `docker exec`:** confirm with `docker compose ps` (compose names them like
  `cloudfuze-mft-postgres-1`).

# Security Model

Selling MFT to regulated enterprises is selling trust. This document describes what is enforced
today and what is planned. It is written to be shown to a customer's security team.

## Threat model (who we defend against)

1. **External attacker** — internet-facing API and transfer endpoints.
2. **Malicious/compromised tenant** — one customer trying to read another's data.
3. **Insider / privileged operator** — someone with database access trying to alter history.
4. **Credential theft** — stolen passwords or tokens.

## Controls implemented in v0.1

### Multi-tenant isolation
- Every tenant-owned entity carries a Hibernate `@TenantId` discriminator. Hibernate appends
  `AND tenant_id = ?` to **every** query and populates it on **every** insert automatically — so
  even a service-layer bug cannot read across tenants.
- The tenant is asserted cryptographically by the JWT and bound per-request in `TenantContext`,
  then always cleared in a `finally` block so no tenant state leaks between pooled threads.
- **Planned defense-in-depth:** PostgreSQL row-level security so the database itself rejects
  cross-tenant access even if the app is bypassed (schema hooks are already in place).

### Credentials
- Passwords hashed with **Argon2id** (OWASP-recommended, memory-hard) — never stored or logged
  in plaintext, never reversible.
- Login is **timing-safe against account enumeration**: an unknown email still runs a full hash
  comparison against a dummy hash, so response time does not reveal which emails exist.
- Failed and successful logins are both audited.

### Authentication & authorization
- Short-lived HS256 JWTs (default 30 min). Secret must be ≥256-bit; the app refuses to start
  with a too-short secret.
- Stateless sessions (no server-side session to steal or fixate).
- Role-based access control (OWNER / ADMIN / OPERATOR / AUDITOR / PARTNER) enforced at the
  endpoint via method security.
- **MFA (TOTP)**: RFC 6238, authenticator-app compatible. The shared secret is stored only
  vault-encrypted; when enabled, login requires a valid 6-digit code (verified end-to-end).
- **SSO (OIDC)**: log in with the customer's IdP (Okta / Azure AD / Keycloak / …). The ID token
  is validated against the IdP's JWKS (signature + issuer + audience) before a session is issued;
  users are matched by verified email within the tenant. Verified end-to-end against Keycloak.

### Tamper-evident audit log
- Append-only. There is **no** update or delete path in the repository or API — by design.
- Each record stores the SHA-256 hash of the previous record, forming a chain. Altering or
  deleting any past record breaks every hash after it.
- Appends are serialized per tenant with a pessimistic lock so the chain can never fork.
- `GET /api/audit/verify` recomputes the entire chain and reports the exact sequence number
  where any tampering occurred. The hashing is a pure function (`AuditHasher`) so a regulator
  can re-verify exported rows independently. **This is the evidence behind the Trust Center.**
- Covered by unit tests that prove tampering is detected.

### Transfer engine (v0.2)
- Files are staged and their **SHA-256 is computed and recorded** so integrity is provable
  end-to-end (verified: upload → SFTP push → SFTP pull is byte-identical).
- Object-store keys are namespaced per tenant; one tenant cannot address another's objects.
- Unhandled errors are logged server-side but never returned to clients (opaque messages only).

### Credential vault (v0.4)
- Partner SFTP credentials are stored encrypted with **AES-256-GCM** (authenticated) — a fresh
  random nonce per secret; plaintext is never persisted or returned by the API (verified: the DB
  row holds only ciphertext, and the partner view exposes `hasStoredSecret`, never the value).
- Covered by unit tests (round-trip, tamper-detection, wrong-key rejection).
- The master key comes from configuration; **in production it must come from KMS/Vault, not env**.

### Hardening (v0.6)
- **SFTP host-key pinning**: a partner's server-key fingerprint can be pinned; any later
  connection whose key doesn't match is rejected (MITM defense). Verified: matched key succeeds,
  a changed key fails with "Server key did not validate".
- **Encrypted Temporal payloads**: an AES-256-GCM payload codec encrypts all workflow/activity
  payloads before they reach Temporal. Verified: the history shows only ciphertext — SFTP
  passwords, hosts, and refs do not appear in cleartext.
- **Login brute-force protection**: 5 failed attempts per account triggers a 15-minute lockout
  (verified: correct password is blocked with 429 during lockout; other accounts unaffected).

### Known gaps to close before selling (tracked on the roadmap)
- Byte-level checkpoint-resume (resume a partial file) is not yet implemented.
- Public/private-key PGP (partner key management); today PGP is passphrase-based.
- SSO (SAML/OIDC) + enforced MFA; move the vault/JWT/codec master keys to KMS/Vault (not env).
- AS2 + MDN and FTPS protocols (feature work, tracked separately).

### API hardening
- CORS locked to configured origins; credentials required.
- CSRF not applicable (stateless bearer-token API).
- Security headers: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`.
- Error responses never leak stack traces, SQL, or which credential field failed.
- Backend container runs as a non-root user.

## On the road to SOC 2 / ISO 27001 (see ROADMAP.md)
- Encryption at rest (envelope encryption + KMS/Vault-managed keys) and in transit (TLS 1.3, PGP).
- SSO (SAML / OIDC) and enforced MFA.
- Secrets management (no secrets in env for production; Vault/cloud secret manager).
- Anomaly detection ("unusual bulk download blocked") and SIEM export (Splunk / Sentinel).
- Dependency scanning, SAST/DAST in CI, and a documented pen-test cadence.

## Reporting a vulnerability
Email security@cloudfuze.com. Do not open public issues for security reports.

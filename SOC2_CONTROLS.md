# SOC 2 (Type II) Control Matrix — CloudFuze MFT

This maps the SOC 2 Trust Services Criteria to the concrete controls in this product and **where an
auditor finds the evidence**. Type II attests that controls *operated effectively over a period*
(typically 3–12 months), so most rows list both the control and the operating evidence to collect.

Legend: ✅ implemented & verified · ◑ implemented, needs operating-period evidence · ⏳ planned.

> This is the technical control inventory owned by Engineering. It feeds — but does not replace —
> the organizational SOC 2 program (policies, HR/vendor controls, the auditor's own testing).

## Security (Common Criteria)

### CC1 — Control environment (organizational)
| # | Control | Evidence | Status |
|---|---------|----------|--------|
| CC1.1 | Code of conduct, security policies, org chart | Company policy set (out of repo) | ⏳ org |
| CC1.4 | Security awareness training | Training records | ⏳ org |

### CC2 / CC3 — Communication & risk assessment
| CC2.1 | System description & data flows | `ARCHITECTURE.md`, `README.md` | ✅ |
| CC3.2 | Risk assessment incl. threat model | `PENTEST.md` §3 (threat-mapped test matrix) | ◑ |

### CC4 — Monitoring of controls
| CC4.1 | Independent evaluation (pen test) + retest | `PENTEST.md` §6–7; firm report + retest letter | ⏳ (blocker) |
| CC4.1 | Continuous control verification | `SecurityCoreIntegrationTest` runs in CI every push (`.github/workflows/ci.yml`) | ✅ |

### CC5 / CC8 — Control activities & change management
| CC8.1 | Changes tested before deploy | CI builds + unit/integration tests + `docker compose config` validation | ✅ |
| CC8.1 | Schema changes controlled & versioned | Flyway migrations `V1..V10`, forward-only, reviewed | ✅ |
| CC8.1 | Peer review before merge | Branch protection + PR review | ◑ (enforce + retain evidence) |

### CC6 — Logical & physical access
| # | Control | Evidence | Status |
|---|---------|----------|--------|
| CC6.1 | Authentication | JWT (HS384, `JwtService`), Argon2id password hashing (`AuthService`) | ✅ |
| CC6.1 | Strong auth / MFA | TOTP MFA enroll+enforce (`MfaService`, `TotpService`); OIDC SSO (`OidcService`) | ✅ |
| CC6.1 | Secrets not in config | `SecretResolver` (`file:`/`env:`) sourced from KMS/Vault; `deploy/secrets/` verified vs real Vault | ✅ |
| CC6.1 | Prod refuses dev-default secrets | `SecretsPolicy` (`@Profile("prod")`) fails startup on default JWT/vault/admin | ✅ |
| CC6.2 | Access provisioning/bootstrap | Bootstrap admin, then `BOOTSTRAP_ENABLED=false`; role assignment | ◑ |
| CC6.3 | Least privilege / RBAC | Roles OWNER/ADMIN/OPERATOR/AUDITOR/PARTNER; `@PreAuthorize` per endpoint | ✅ |
| CC6.3 | DB least privilege | App runs as non-superuser `mft_app`; migrations as owner (`db/init/01-app-role.sql`) | ✅ |
| CC6.1 | Tenant isolation (app) | Hibernate `@TenantId` + tenant-scoped `findScopedById` (no IDOR) | ✅ |
| CC6.1 | Tenant isolation (DB, defense-in-depth) | Forced Postgres RLS on `app.tenant_id` GUC (migration `V10`); proven in integration test | ✅ |
| CC6.6 | Encryption in transit | TLS 1.2/1.3 + HSTS at ingress (`deploy/tls/`, verified); SFTP + PGP for partner exchange | ✅ |
| CC6.7 | Encryption at rest | AES-256-GCM credential vault (`CryptoVault`); encrypted Temporal payloads (`EncryptionCodec`); PGP file payloads | ✅ |
| CC6.7 | Key management & rotation | Keys via KMS/Vault seam; rotation procedure in `deploy/secrets/README.md`; envelope key-versioning ⏳ | ◑ |
| CC6.8 | Malware/integrity of transfers | SHA-256 integrity check on transferred files; host-key pinning | ✅ |

### CC7 — System operations
| CC7.1 | Vulnerability management | Dependency & container scanning (Trivy/Dependabot) in CI | ⏳ |
| CC7.2 | Security monitoring / logging | Tamper-evident hash-chained audit log (`AuditService`, per-tenant seq, SHA-256 chain) | ✅ |
| CC7.2 | Log export to SIEM | Cursor-based, at-least-once export to Splunk/Sentinel (`SiemForwarder`, JSON+CEF) | ✅ |
| CC7.2 | Audit integrity provable | Independent `verifyChain()`; DB-tamper detection proven in integration test | ✅ |
| CC7.3 | Anomaly detection | SIEM-side alerting today; in-product anomaly detection | ⏳ |
| CC7.4 | Incident response | IR runbook + on-call rotation | ⏳ (blocker for regulated) |

## Availability
| A1.1 | Capacity / health | `/actuator/health` probes; resource limits + autoscaling | ◑ |
| A1.2 | Durable execution | Temporal durable + scheduled workflows (crash-resume verified) | ✅ |
| A1.2 | Backup & recovery | `deploy/backup/backup.sh` + `restore.sh`; RPO/RTO + tested restore in `DR_RUNBOOK.md` (roundtrip verified 2026-07-09) | ✅ |
| A1.3 | Recovery testing | Quarterly restore drill; log in `DR_RUNBOOK.md` | ◑ (recurring evidence) |

## Confidentiality
| C1.1 | Data classification & handling | Encrypted credentials + payloads; least-privilege access | ◑ |
| C1.2 | Secure disposal | Staging files removed post-transfer; retention/purge policy | ◑ |

## Processing Integrity
| PI1.1 | Complete & accurate processing | Hash-chained audit of every transfer state transition (incl. filename/dest); SHA-256 file integrity | ✅ |
| PI1.5 | Durable, exactly-once-intended workflows | Temporal workflow determinism + idempotent activities | ✅ |

## Privacy
| P-series | If PII/regulated data in transfers | Encryption, access control, audit already apply; DPA + data-subject processes | ⏳ org |

---

## Evidence index (for the auditor)

| Evidence | Location |
|----------|----------|
| Multi-tenant isolation + audit-tamper test | `backend/src/test/.../SecurityCoreIntegrationTest.java` |
| CI pipeline (tests run every push) | `.github/workflows/ci.yml` |
| RLS migration | `backend/src/main/resources/db/migration/V10__row_level_security.sql` |
| Non-superuser app role | `db/init/01-app-role.sql` |
| Secret seam + KMS/Vault wiring | `backend/.../crypto/SecretResolver.java`, `deploy/secrets/` |
| Prod secret guardrail | `backend/.../crypto/SecretsPolicy.java` |
| TLS config | `deploy/tls/nginx-tls.conf` |
| Backup/restore + DR runbook | `deploy/backup/` |
| Audit hash chain | `backend/.../audit/AuditHasher.java`, `AuditService.java` |
| Pen-test scope & readiness | `PENTEST.md` |
| Go-live blockers | `PRODUCTION_READINESS.md` §2 |

## Open items before a Type II report

1. Engage the independent pen test (CC4.1) — see `PENTEST.md`.
2. Add dependency + container scanning to CI (CC7.1).
3. Write & test the incident-response runbook (CC7.4).
4. Establish the operating-period evidence trail (access reviews, change-review records, quarterly
   restore drills) — Type II tests *operation over time*, not just design.
5. Complete the organizational program (policies, training, vendor management) with the auditor.

# Secrets wiring — KMS / Vault → CloudFuze MFT

CloudFuze MFT never stores master secrets (JWT signing key, crypto-vault master key, DB password)
as plaintext in config. The app obtains every secret through one seam: `SecretResolver`, which
accepts three reference forms:

| Reference | Resolves to | Use |
|-----------|-------------|-----|
| `file:/path` | the file's contents (trimmed) | **production** — KMS/Vault values mounted as files |
| `env:NAME` | environment variable `NAME` | CI / simple deployments |
| any literal | the string itself | dev/local only (rejected in `prod` profile by `SecretsPolicy`) |

Because the production path is just "read a file", **any** secret backend that can materialize a
value to a file works with zero application code change:

- **HashiCorp Vault** — the scripts here (`vault-bootstrap.sh`, `materialize-secrets.sh`).
- **Kubernetes** — External Secrets Operator or the Secrets Store CSI driver mount AWS KMS /
  Azure Key Vault / GCP Secret Manager / Vault values as files at the paths you point the app at.
- **Docker secrets** — mounted under `/run/secrets/...`.
- **AWS** — the ECS/EKS secrets integration, or fetch-at-boot in an init container.

## Vault reference flow (verified)

```bash
# 1. Seed the master secrets into Vault (once; prod generates these in your KMS/PKI).
export VAULT_ADDR=https://vault.internal:8200 VAULT_TOKEN=...
./vault-bootstrap.sh

# 2. At container/host startup, an init step fetches them to a tmpfs mount.
export SECRETS_DIR=/run/mft-secrets
./materialize-secrets.sh

# 3. Point the backend at the files.
export JWT_SECRET=file:/run/mft-secrets/jwt-secret
export CRYPTO_MASTER_KEY=file:/run/mft-secrets/crypto-master-key
# DB_PASSWORD likewise via a file: ref in application.yml
```

The app process holds no Vault token and links no KMS SDK — it only reads files. The privilege to
talk to Vault/KMS lives in the short-lived init step, not the long-running app.

**Verified 2026-07-09** against a real HashiCorp Vault dev server: seeded `mft/jwt`, `mft/crypto`,
`mft/db` (KV v2) → materialized to files (mode 600) → confirmed the crypto master key decodes to
exactly 32 bytes (the `CryptoVault` AES-256 requirement) and each file's contents byte-match the
value stored in Vault.

## Calling a KMS SDK directly (optional)

If you prefer decrypting a wrapped key at boot instead of mounting a file, add a `kms:` branch to
`SecretResolver.resolve(...)` that calls the provider SDK (e.g. AWS KMS `Decrypt`). Everything
downstream already obtains keys through the resolver, so no other code changes.

## Rotation

- **JWT secret** — rotate by supporting a key-id/kid list; issue with the new key, keep validating
  the previous key until old tokens expire.
- **Crypto master key** — envelope-encrypt per-record data keys so the master can rotate without
  re-encrypting every credential (roadmap: key-versioned envelope encryption).
- **DB password** — rotate in Vault, re-materialize, rolling-restart the backend.

Rotation cadence and dual-control for key access are documented in `SOC2_CONTROLS.md` (CC6.1).

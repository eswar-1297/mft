#!/usr/bin/env bash
# Materialize MFT master secrets from HashiCorp Vault into files the backend reads via the
# SecretResolver `file:` reference form. This is the init-container / sidecar pattern: secrets live
# in Vault (or AWS KMS / Azure Key Vault), are fetched with a short-lived token at startup, and
# written to a tmpfs mount that the app process reads. The app never holds a Vault token or a KMS
# SDK — it only reads files, so swapping the secret backend needs zero app changes.
#
# In Kubernetes the equivalent is the External Secrets Operator or the Secrets Store CSI driver,
# which mount KMS/Vault values as files at the same paths. This script is the docker-compose /
# bare-metal analogue.
#
# Env: VAULT_ADDR, VAULT_TOKEN, SECRETS_DIR (default /run/mft-secrets)
set -euo pipefail
export VAULT_ADDR="${VAULT_ADDR:-http://127.0.0.1:8200}"
SECRETS_DIR="${SECRETS_DIR:-/run/mft-secrets}"

mkdir -p "$SECRETS_DIR"
chmod 700 "$SECRETS_DIR"

# -field extracts a single value with no wrapping JSON/whitespace.
vault kv get -field=secret     mft/jwt    > "$SECRETS_DIR/jwt-secret"
vault kv get -field=master_key mft/crypto > "$SECRETS_DIR/crypto-master-key"
vault kv get -field=password   mft/db     > "$SECRETS_DIR/db-password"

chmod 600 "$SECRETS_DIR"/*
echo "Materialized secrets to $SECRETS_DIR:"
ls -l "$SECRETS_DIR"
echo
echo "Point the backend at them, e.g.:"
echo "  JWT_SECRET=file:$SECRETS_DIR/jwt-secret"
echo "  CRYPTO_MASTER_KEY=file:$SECRETS_DIR/crypto-master-key"
echo "  DB_PASSWORD (read via file: ref in application.yml)"

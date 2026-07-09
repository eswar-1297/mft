#!/usr/bin/env bash
# Generate a self-signed TLS cert for local/dev TLS termination.
# PRODUCTION: do NOT use self-signed certs — use a CA-issued cert (Let's Encrypt / ACM / your PKI).
# This exists so the `tls` compose profile and CI can exercise the real TLS path end-to-end.
set -euo pipefail
cd "$(dirname "$0")"

# Git-Bash/MSYS rewrites leading-slash args like "/CN=..." into Windows paths; disable that.
export MSYS_NO_PATHCONV=1

DAYS="${1:-365}"
CN="${TLS_CN:-localhost}"

openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout server.key -out server.crt \
  -days "$DAYS" -subj "/CN=${CN}" \
  -addext "subjectAltName=DNS:localhost,DNS:${CN},IP:127.0.0.1"

chmod 600 server.key
echo "Wrote server.crt / server.key (CN=${CN}, ${DAYS} days). Self-signed — dev only."

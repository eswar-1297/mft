#!/usr/bin/env bash
# Restore CloudFuze MFT from a backup produced by backup.sh.
# Usage: restore.sh <backup-dir>   (e.g. ./backups/20260709T120000Z)
#
# Restores the PostgreSQL database (dropping/recreating the schema) and re-uploads object-store
# payloads. Run against a freshly provisioned, EMPTY target — never against live production data
# without a maintenance window. Verify against MANIFEST.txt before running.
set -euo pipefail

SRC="${1:?usage: restore.sh <backup-dir>}"
[ -f "${SRC}/postgres.dump" ] || { echo "No postgres.dump in ${SRC}"; exit 1; }

PGHOST="${PGHOST:-localhost}"; PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-mft}"; PGDATABASE="${PGDATABASE:-mft}"
export PGPASSWORD="${PGPASSWORD:-mft}"

echo "[1/2] Restoring PostgreSQL into ${PGUSER}@${PGHOST}:${PGPORT}/${PGDATABASE} ..."
# --clean --if-exists drops existing objects first so the restore is idempotent; owner is remapped
# to the connecting role.
pg_restore -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" \
    --clean --if-exists --no-owner "${SRC}/postgres.dump"

echo "[2/2] Restoring object store ..."
if [ -d "${SRC}/objectstore" ] && command -v mc >/dev/null 2>&1; then
    mc alias set mftrst "${S3_ENDPOINT:-http://localhost:9000}" \
        "${S3_ACCESS_KEY:-mftminio}" "${S3_SECRET_KEY:-mftminio123}" >/dev/null
    mc mb --ignore-existing "mftrst/${S3_BUCKET:-mft-files}" >/dev/null 2>&1 || true
    mc mirror --overwrite "${SRC}/objectstore" "mftrst/${S3_BUCKET:-mft-files}" || true
else
    echo "    (no object-store snapshot or mc missing — skipping)"
fi

echo "Restore complete from ${SRC}"

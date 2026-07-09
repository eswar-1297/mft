#!/usr/bin/env bash
# Back up CloudFuze MFT state: the PostgreSQL database (all tenants, audit log, workflows) and the
# object store (transferred file payloads). Produces a timestamped, restorable snapshot.
#
# For a real deployment, run this on a schedule (cron/k8s CronJob) and ship the output to
# off-host, versioned, encrypted storage (S3 with object-lock, etc.). For point-in-time recovery
# beyond these logical dumps, also enable Postgres WAL archiving / a managed PITR-capable service.
#
# Env:
#   PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE  — Postgres connection (admin/owner role)
#   S3_ENDPOINT/S3_ACCESS_KEY/S3_SECRET_KEY/S3_BUCKET — object store
#   BACKUP_DIR  — where to write (default ./backups)
set -euo pipefail

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
OUT="${BACKUP_DIR}/${STAMP}"
mkdir -p "$OUT"

PGHOST="${PGHOST:-localhost}"; PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-mft}"; PGDATABASE="${PGDATABASE:-mft}"
export PGPASSWORD="${PGPASSWORD:-mft}"

echo "[1/3] Dumping PostgreSQL ${PGUSER}@${PGHOST}:${PGPORT}/${PGDATABASE} ..."
# Custom format (-Fc): compressed, parallelizable, restorable with pg_restore.
pg_dump -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -Fc -f "${OUT}/postgres.dump"

echo "[2/3] Mirroring object store bucket '${S3_BUCKET:-mft-files}' ..."
if command -v mc >/dev/null 2>&1; then
    mc alias set mftbak "${S3_ENDPOINT:-http://localhost:9000}" \
        "${S3_ACCESS_KEY:-mftminio}" "${S3_SECRET_KEY:-mftminio123}" >/dev/null
    mc mirror --overwrite "mftbak/${S3_BUCKET:-mft-files}" "${OUT}/objectstore" || true
else
    echo "    (mc not found — skipping object-store mirror; install MinIO client for full backup)"
fi

echo "[3/3] Writing manifest ..."
cat > "${OUT}/MANIFEST.txt" <<EOF
CloudFuze MFT backup
created_utc: ${STAMP}
postgres: ${PGUSER}@${PGHOST}:${PGPORT}/${PGDATABASE}
object_store: ${S3_ENDPOINT:-http://localhost:9000}/${S3_BUCKET:-mft-files}
postgres_dump_sha256: $(sha256sum "${OUT}/postgres.dump" | cut -d' ' -f1)
EOF

echo "Backup complete: ${OUT}"

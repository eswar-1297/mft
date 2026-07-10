# Local dev stack (no Docker, no admin rights)

For machines that can't run Docker Desktop or don't have admin rights to install
Java/Postgres system-wide, the whole product can still run from portable binaries
plus these two scripts.

## One-time setup

Download portable copies of the toolchain into a folder of your choice (default
expected: `%USERPROFILE%\mft-toolchain`, override with the `MFT_TOOLCHAIN_DIR`
env var):

| Tool | Source |
|---|---|
| JDK 21 | [Adoptium Temurin 21 (Windows x64 zip)](https://adoptium.net/temurin/releases/?version=21) |
| Maven 3.9.x | [Apache Maven binaries](https://maven.apache.org/download.cgi) |
| PostgreSQL 16 | [EDB PostgreSQL Windows binaries zip](https://www.enterprisedb.com/download-postgresql-binaries) |
| MinIO | [dl.min.io server/minio/release/windows-amd64/minio.exe](https://min.io/download) |
| Temporal CLI | [temporal.download/cli](https://temporal.io/setup/install-temporal-cli) |

Extract each into `<toolchain>\jdk`, `<toolchain>\maven`, `<toolchain>\pg`, and
place `minio.exe` / `temporal\temporal.exe` directly under `<toolchain>`.

Also compile the tiny embedded SFTP test server (used as a stand-in partner for
local Transfers/Workflows testing) — source lives at `local-dev/SftpTestServer.java`,
built against the `sshd-core`/`sshd-sftp`/`slf4j-api` jars already pulled into your
local Maven repo by `mvn` (`~/.m2/repository/org/apache/sshd/...`).

## Running it

```powershell
./local-dev/start-mft.ps1
```

Starts, in order: Postgres (`:5433`), MinIO (`:9000`/console `:9001`), Temporal
(`:7233`/UI `:8233`), the demo SFTP server (`:2222`), the backend (`:8080`, built
from `backend/target/mft-*.jar` — run `mvn -DskipTests clean package` first), and
the frontend (`:5173`). Opens the console in your browser when ready.

Login: `demo` / `admin@cloudfuze.com` / `ChangeMe!2026` (bootstrap defaults —
override via `.env` per the root README before any real use).

```powershell
./local-dev/stop-mft.ps1
```

Stops everything cleanly.

## Why this exists

Full `docker compose up` remains the primary, recommended way to run this project
— see the root [README](../README.md). This local-dev path is a fallback for
environments where Docker isn't available, using the same backend flag
(`MFT_STARTUP_CONNECT_EXTERNAL=false`) the integration tests use to boot without
external services reachable, except here we actually stand up lightweight
substitutes for MinIO/Temporal/SFTP so the full product — transfers, workflows,
Ad-hoc Send — works end to end.

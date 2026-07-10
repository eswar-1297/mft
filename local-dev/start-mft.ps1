# CloudFuze MFT - local stack launcher (no admin, no Docker). See local-dev/README.md for setup.
# Starts Postgres, MinIO, Temporal, the demo SFTP server, the backend, and the frontend,
# waits for each to be ready, then opens the web console. Safe to re-run: it skips anything
# already listening.

$ErrorActionPreference = 'Continue'

$APP    = Split-Path -Parent $PSScriptRoot
$TOOLS  = if ($env:MFT_TOOLCHAIN_DIR) { $env:MFT_TOOLCHAIN_DIR } else { "$env:USERPROFILE\mft-toolchain" }
$JAVA   = Join-Path $TOOLS "jdk\*\bin\java.exe"
$JAVA   = (Get-Item $JAVA -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
$PGBIN  = "$TOOLS\pg\pgsql\bin"
$PGDATA = "$TOOLS\pgdata"
$M2     = "$env:USERPROFILE\.m2\repository"

if (-not $JAVA -or -not (Test-Path $PGBIN)) {
  Write-Host "Toolchain not found under $TOOLS - see local-dev/README.md to set it up first." -ForegroundColor Red
  Write-Host "Override the location with the MFT_TOOLCHAIN_DIR environment variable if it lives elsewhere." -ForegroundColor Yellow
  exit 1
}

function Listening($port) {
  [bool](Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
}
function WaitFor($port, $secs, $label) {
  Write-Host ("Waiting for {0} (:{1})..." -f $label, $port) -NoNewline
  for ($i = 0; $i -lt $secs; $i++) {
    if (Listening $port) { Write-Host " ready"; return $true }
    Start-Sleep 1; Write-Host "." -NoNewline
  }
  Write-Host " TIMEOUT"; return $false
}

Write-Host "=== Starting CloudFuze MFT local stack ===" -ForegroundColor Cyan

# 1) PostgreSQL :5433
if (Listening 5433) { Write-Host "Postgres already running on :5433" }
else {
  Write-Host "Starting Postgres :5433"
  & "$PGBIN\pg_ctl.exe" -D $PGDATA -o "-p 5433" -l "$TOOLS\pg.log" start | Out-Null
}

# 2) MinIO :9000
if (Listening 9000) { Write-Host "MinIO already running on :9000" }
else {
  Write-Host "Starting MinIO :9000"
  $env:MINIO_ROOT_USER = "mftminio"; $env:MINIO_ROOT_PASSWORD = "mftminio123"
  Start-Process -WindowStyle Minimized -FilePath "$TOOLS\minio.exe" `
    -ArgumentList "server","$TOOLS\minio-data","--address","127.0.0.1:9000","--console-address","127.0.0.1:9001"
}

# 3) Temporal :7233 (UI :8233)
if (Listening 7233) { Write-Host "Temporal already running on :7233" }
else {
  Write-Host "Starting Temporal :7233"
  Start-Process -WindowStyle Minimized -FilePath "$TOOLS\temporal\temporal.exe" `
    -ArgumentList "server","start-dev","--ip","127.0.0.1","--port","7233","--ui-port","8233","--namespace","default"
}

# 4) Demo SFTP server :2222
if (Listening 2222) { Write-Host "SFTP server already running on :2222" }
else {
  Write-Host "Starting SFTP server :2222"
  $cp = @(
    "$TOOLS\sftp-classes",
    "$M2\org\apache\sshd\sshd-common\2.13.2\sshd-common-2.13.2.jar",
    "$M2\org\apache\sshd\sshd-core\2.13.2\sshd-core-2.13.2.jar",
    "$M2\org\apache\sshd\sshd-sftp\2.13.2\sshd-sftp-2.13.2.jar",
    "$M2\org\slf4j\slf4j-api\1.7.36\slf4j-api-1.7.36.jar"
  ) -join ";"
  Start-Process -WindowStyle Minimized -FilePath $JAVA `
    -ArgumentList "-cp",$cp,"-Dsftp.port=2222","-Dsftp.root=$TOOLS\sftp-root","SftpTestServer"
}

WaitFor 9000 30 "MinIO"    | Out-Null
WaitFor 7233 40 "Temporal" | Out-Null
WaitFor 2222 20 "SFTP"     | Out-Null

# 5) Backend :8080
if (Listening 8080) { Write-Host "Backend already running on :8080" }
else {
  $jar = Get-ChildItem "$APP\backend\target\mft-*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
  if (-not $jar) {
    Write-Host "No backend jar found under backend/target - run 'mvn -DskipTests clean package' in backend/ first." -ForegroundColor Red
  } else {
    Write-Host "Starting Backend :8080"
    $env:MFT_STARTUP_CONNECT_EXTERNAL = "true"
    $env:DB_URL       = "jdbc:postgresql://127.0.0.1:5433/mft"
    $env:DB_USER      = "mft_app";  $env:DB_PASSWORD = "mft_app"
    $env:DB_ADMIN_URL = "jdbc:postgresql://127.0.0.1:5433/mft"
    $env:DB_ADMIN_USER= "mft";      $env:DB_ADMIN_PASSWORD = "mft"
    $env:CORS_ORIGINS = "http://localhost:5173"
    $env:S3_ENDPOINT  = "http://127.0.0.1:9000"
    $env:S3_ACCESS_KEY= "mftminio"; $env:S3_SECRET_KEY = "mftminio123"
    $env:S3_BUCKET    = "mft-files"; $env:S3_PATH_STYLE = "true"
    $env:TEMPORAL_TARGET = "127.0.0.1:7233"
    Start-Process -WindowStyle Minimized -FilePath $JAVA -ArgumentList "-jar",$jar.FullName
  }
}
WaitFor 8080 90 "Backend" | Out-Null

# 6) Frontend :5173
if (Listening 5173) { Write-Host "Frontend already running on :5173" }
else {
  Write-Host "Starting Frontend :5173"
  Start-Process -WindowStyle Minimized -FilePath "cmd.exe" `
    -ArgumentList "/c","cd /d `"$APP\frontend`" && npm run dev"
}
WaitFor 5173 60 "Frontend" | Out-Null

Start-Process "http://localhost:5173"
Write-Host ""
Write-Host "===================================================" -ForegroundColor Green
Write-Host " CloudFuze MFT is UP." -ForegroundColor Green
Write-Host "  Web console : http://localhost:5173"
Write-Host "  Login       : demo / admin@cloudfuze.com / ChangeMe!2026"
Write-Host "  Temporal UI : http://localhost:8233"
Write-Host "  MinIO console: http://localhost:9001  (mftminio / mftminio123)"
Write-Host "  Demo SFTP   : 127.0.0.1:2222  (user: foo, any password)"
Write-Host "===================================================" -ForegroundColor Green
Read-Host "Press Enter to close this window (services keep running)"

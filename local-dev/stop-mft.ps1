# Stops the CloudFuze MFT local stack started by start-mft.ps1. See local-dev/README.md.
$TOOLS  = if ($env:MFT_TOOLCHAIN_DIR) { $env:MFT_TOOLCHAIN_DIR } else { "$env:USERPROFILE\mft-toolchain" }
$PGBIN  = "$TOOLS\pg\pgsql\bin"
$PGDATA = "$TOOLS\pgdata"

Write-Host "Stopping CloudFuze MFT services..." -ForegroundColor Cyan
# Frontend, backend, SFTP, MinIO API+console, Temporal server+UI
foreach ($p in 5173,8080,2222,9000,9001,7233,8233) {
  Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue | ForEach-Object {
    try { Stop-Process -Id $_.OwningProcess -Force -ErrorAction Stop; Write-Host "  stopped PID $($_.OwningProcess) on :$p" } catch {}
  }
}
# Postgres (graceful)
if (Test-Path "$PGBIN\pg_ctl.exe") {
  & "$PGBIN\pg_ctl.exe" -D $PGDATA stop 2>$null | Out-Null
  Write-Host "  stopped Postgres (:5433)"
}
Write-Host "All stopped." -ForegroundColor Green

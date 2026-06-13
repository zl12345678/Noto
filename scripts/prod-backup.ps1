# PostgreSQL 备份（Docker noto-db 容器）
# 用法：.\scripts\prod-backup.ps1
#       .\scripts\prod-backup.ps1 -OutDir D:\backups\noto

param(
    [string]$OutDir = ''
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib\Noto-Docker.ps1')

$db = Get-NotoDatabaseName
$user = Get-NotoPostgresUser
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'

if (-not $OutDir) {
    $OutDir = Join-Path $script:NotoRoot 'backups'
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$outFile = Join-Path $OutDir "noto-$timestamp.sql"
$dockerDb = docker ps --filter 'name=noto-db' --format '{{.Names}}' 2>$null | Select-Object -First 1

Write-Host "== Backup $db -> $outFile ==" -ForegroundColor Cyan

if (-not $dockerDb) {
    Write-Host 'noto-db container not running.' -ForegroundColor Red
    Write-Host 'Start stack first: .\scripts\prod-up.ps1' -ForegroundColor Gray
    exit 1
}

docker exec $dockerDb pg_dump -U $user $db | Out-File -FilePath $outFile -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed' }
Write-Host "Backup saved: $outFile" -ForegroundColor Green
Write-Host 'Linux servers: use deploy/prod-backup.sh for .sql.gz' -ForegroundColor Gray

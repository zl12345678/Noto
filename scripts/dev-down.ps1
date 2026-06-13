# 停止本地开发基础设施（db + minio，保留数据卷）
# 用法：.\scripts\dev-down.ps1

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib\Noto-Docker.ps1')

Write-Host '== Stop Noto dev infrastructure ==' -ForegroundColor Cyan

Invoke-InNotoRoot {
    docker compose stop db minio
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host 'Stopped: noto-db, noto-minio (volumes kept)' -ForegroundColor Green
    Write-Host 'Remove containers: docker compose down'
    Write-Host 'Remove data too:    docker compose down -v   # caution'
}

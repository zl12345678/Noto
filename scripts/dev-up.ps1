# Noto 本地开发基础设施（Docker Desktop）
# 启动 PostgreSQL（pgvector）+ MinIO，匹配 application-dev.yml
# 用法：.\scripts\dev-up.ps1

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib\Noto-Docker.ps1')

Write-Host '== Noto dev infrastructure ==' -ForegroundColor Cyan

Ensure-NotoEnvFile

Invoke-InNotoRoot {
    Start-NotoComposeServices @('db', 'minio')

    $dbReady = Wait-NotoContainerHealthy -ContainerName 'noto-db' -TimeoutSeconds 30
    if (-not $dbReady) {
        Write-Host 'Database not healthy yet — check: docker logs noto-db' -ForegroundColor Yellow
    }

    Initialize-NotoPgvector

    Write-Host ''
    Show-NotoContainers
    Write-NotoDevNextSteps
}

# 生产 Compose 启动（合并 deploy/docker-compose.prod.yml）
# - db/minio 不映射公网端口；frontend/backend 仅 127.0.0.1
# 用法：.\scripts\prod-up.ps1
#       .\scripts\prod-up.ps1 -SkipCheck   # 跳过 .env 检查（不推荐）

param(
    [switch]$SkipCheck,
    [switch]$BuildOnly
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib\Noto-Docker.ps1')

Write-Host '== Noto production deploy ==' -ForegroundColor Cyan

Ensure-NotoProdEnvFile

if (-not $SkipCheck) {
    if (-not (Test-NotoProdEnv -Strict)) {
        Write-Host 'Aborting. Fix .env or use -SkipCheck (not recommended).' -ForegroundColor Red
        exit 1
    }
}

Invoke-InNotoRoot {
    if ($BuildOnly) {
        Invoke-NotoProdCompose @('build')
        Write-Host 'Build complete.' -ForegroundColor Green
        return
    }

    Write-Host 'Starting production stack (compose merge)...' -ForegroundColor Gray
    Invoke-NotoProdCompose @('up', '-d', '--build')

    $healthy = $false
    for ($i = 0; $i -lt 60; $i++) {
        try {
            $resp = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/v1/health' -TimeoutSec 3
            if ($resp.code -eq 0) { $healthy = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }

    Write-Host ''
    Show-NotoContainers
    if ($healthy) {
        Write-Host 'Health check: OK' -ForegroundColor Green
    } else {
        Write-Host 'Health check: pending — docker compose logs -f backend' -ForegroundColor Yellow
    }
    Write-NotoProdNextSteps
}

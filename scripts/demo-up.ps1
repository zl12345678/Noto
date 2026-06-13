# Noto 一键演示栈（Docker Desktop）
# 默认：db + minio + backend + frontend，并预置 admin 演示数据
# 用法：
#   .\scripts\demo-up.ps1                  # 全栈 Docker 演示
#   .\scripts\demo-up.ps1 -InfraOnly       # 仅 db + minio（配合 IDE 本地后端）

param(
    [switch]$InfraOnly,
    [Alias('KeepLocalBackend')]
    [switch]$KeepLocalBackend
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib\Noto-Docker.ps1')

if ($KeepLocalBackend -and -not $InfraOnly) {
    Write-Host 'Note: -KeepLocalBackend is deprecated; use -InfraOnly for IDE backend mode.' -ForegroundColor Yellow
    $InfraOnly = $true
}

Write-Host '== Noto Demo Stack ==' -ForegroundColor Cyan

Ensure-NotoEnvFile

Invoke-InNotoRoot {
    if ($InfraOnly) {
        Start-NotoComposeServices @('db', 'minio')
        Wait-NotoContainerHealthy -ContainerName 'noto-db' -TimeoutSeconds 30 | Out-Null
        Initialize-NotoPgvector
        Write-Host ''
        Show-NotoContainers
        Write-NotoDevNextSteps
        Write-Host 'Re-seed demo data: .\scripts\reset-demo-seed.ps1  then restart backend' -ForegroundColor Gray
        return
    }

    Stop-NotoProcessOnPort -Port 9086 | Out-Null

    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    docker rm -f noto-backend noto-frontend 2>$null | Out-Null
    $ErrorActionPreference = $prevEap

    Ensure-NotoComposeService 'db'
    Ensure-NotoComposeService 'minio'

    Write-Host 'Building backend & frontend (first run may take a few minutes)...' -ForegroundColor Gray
    $ErrorActionPreference = 'Continue'
    docker compose up -d --build backend frontend 2>&1 | ForEach-Object { Write-Host $_ }
    $buildExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($buildExit -ne 0) { throw 'docker compose up failed' }

    if (Wait-NotoBackendHealth) {
        $seedLog = docker logs noto-backend 2>&1 | Select-String 'Demo data seeded'
        if ($seedLog) {
            Write-Host $seedLog.Line -ForegroundColor Green
        } else {
            Write-Host 'Demo data already seeded (first run only).' -ForegroundColor Gray
            Write-Host 'Re-seed: .\scripts\reset-demo-seed.ps1  then docker compose restart backend'
        }
    } else {
        Write-Host 'Check: docker logs noto-backend' -ForegroundColor Yellow
    }

    Write-Host ''
    docker ps --filter 'name=noto-' --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
    Write-NotoDemoBanner
}

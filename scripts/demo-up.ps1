# Noto 一键演示栈（Docker Desktop）
# 启动 db + minio + backend + frontend，并预置 admin 演示数据
# 用法：.\scripts\demo-up.ps1

param(
    [switch]$KeepLocalBackend
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

Write-Host "== Noto Demo Stack ==" -ForegroundColor Cyan

$envFile = Join-Path $root ".env"
$exampleFile = Join-Path $root ".env.example"
if (-not (Test-Path $envFile)) {
    Copy-Item $exampleFile $envFile
    Write-Host "Created .env from .env.example"
}

Push-Location $root
try {
    if (-not $KeepLocalBackend) {
        $backendConn = Get-NetTCPConnection -LocalPort 9086 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($backendConn) {
            $backendPid = $backendConn.OwningProcess
            Write-Host "Port 9086 is used by PID $backendPid (local backend). Stopping for Docker demo..." -ForegroundColor Yellow
            Stop-Process -Id $backendPid -Force -ErrorAction SilentlyContinue
            Start-Sleep -Seconds 2
        }
    }

    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    docker rm -f noto-backend noto-frontend 2>$null | Out-Null
    $ErrorActionPreference = $prevEap

    function Ensure-ComposeService([string]$service) {
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $output = docker compose up -d $service 2>&1
        $exit = $LASTEXITCODE
        $ErrorActionPreference = $prevEap
        if ($exit -ne 0 -or ($output | Out-String) -match "Error response") {
            Write-Host "Recreating orphan container for $service ..." -ForegroundColor Yellow
            if ($service -eq "minio") { docker rm -f noto-minio 2>$null | Out-Null }
            if ($service -eq "db") { docker rm -f noto-db 2>$null | Out-Null }
            docker compose up -d $service
            if ($LASTEXITCODE -ne 0) { throw "Failed to start $service" }
        }
    }

    Ensure-ComposeService "db"
    Ensure-ComposeService "minio"

    Write-Host "Building backend & frontend (first run may take a few minutes)..." -ForegroundColor Gray
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    docker compose up -d --build backend frontend 2>&1 | ForEach-Object { Write-Host $_ }
    $buildExit = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    if ($buildExit -ne 0) { throw "docker compose up failed" }

    Write-Host ""
    Write-Host "Waiting for backend health..." -ForegroundColor Gray
    $healthy = $false
    for ($i = 0; $i -lt 60; $i++) {
        try {
            $resp = Invoke-RestMethod -Uri "http://localhost:9086/api/v1/health" -TimeoutSec 3
            if ($resp.code -eq 0) { $healthy = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }

    if (-not $healthy) {
        Write-Host "Backend not ready. Check: docker logs noto-backend" -ForegroundColor Yellow
    } else {
        Write-Host "Backend is UP" -ForegroundColor Green
        $seedLog = docker logs noto-backend 2>&1 | Select-String "Demo data seeded"
        if ($seedLog) {
            Write-Host $seedLog.Line -ForegroundColor Green
        } else {
            Write-Host "Demo data already seeded (first run only). To re-seed v2:" -ForegroundColor Gray
            Write-Host '  docker exec noto-db psql -U postgres -d noto_zhihui_dev -c "DELETE FROM user_setting WHERE setting_key=''noto.demo.seeded'';"'
            Write-Host "  docker compose restart backend"
        }
    }

    Write-Host ""
    docker ps --filter "name=noto-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    Write-Host ""
    Write-Host "========== Demo Ready ==========" -ForegroundColor Cyan
    Write-Host "  Web UI:   http://localhost:8080"
    Write-Host "  API:      http://localhost:9086/api/v1/health"
    Write-Host "  Login:    admin / admin123"
    Write-Host "  MinIO UI: http://localhost:9001  (minioadmin / minioadmin)"
    Write-Host ""
    Write-Host "Flow: 周会纪要 -> 规则提取待办 -> 首页看板拖拽 -> Ctrl+K 搜 Q2"
    Write-Host "Stop: docker compose down"
    Write-Host "Dev mode (IDE backend): .\\scripts\\dev-up.ps1  then use -KeepLocalBackend next time"
} finally {
    Pop-Location
}

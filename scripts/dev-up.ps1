# Noto 本地开发基础设施（Docker Desktop）
# 启动 PostgreSQL（pgvector）+ MinIO，匹配 application-dev.yml
# 用法：.\scripts\dev-up.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

Write-Host "== Noto dev infrastructure ==" -ForegroundColor Cyan

if (-not (Test-Path (Join-Path $root ".env"))) {
    Copy-Item (Join-Path $root ".env.example") (Join-Path $root ".env")
    Write-Host "Created .env from .env.example" -ForegroundColor Yellow
}

Push-Location $root
try {
    docker compose up -d db minio
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Write-Host ""
    Write-Host "Waiting for PostgreSQL..." -ForegroundColor Gray
    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        $health = docker inspect -f "{{.State.Health.Status}}" noto-db 2>$null
        if ($health -eq "healthy") { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) {
        Write-Host "Database not healthy yet — check: docker logs noto-db" -ForegroundColor Yellow
    }

    $image = docker inspect -f "{{.Config.Image}}" noto-db 2>$null
    Write-Host ""
    Write-Host "PostgreSQL image: $image"
    if ($image -notmatch "pgvector") {
        Write-Host "WARNING: not using pgvector image — RAG will fall back to in-memory scan." -ForegroundColor Yellow
        Write-Host "Fix: docker compose down db; docker rm -f noto-db; docker compose up -d db"
    } else {
        docker exec noto-db psql -U postgres -d noto_zhihui_dev -c "CREATE EXTENSION IF NOT EXISTS vector;" 2>$null | Out-Null
        $ext = docker exec noto-db psql -U postgres -d noto_zhihui_dev -tAc "SELECT extversion FROM pg_extension WHERE extname='vector';" 2>$null
        if ($ext) {
            Write-Host "pgvector extension: $ext" -ForegroundColor Green
        }
    }

    Write-Host ""
    docker ps --filter "name=noto-" --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Cyan
    Write-Host "  1. Backend:  cd backend; .\dev.ps1 -WatchCompile   (or run ZhihuiApplication in IDE)"
    Write-Host "  2. Frontend: cd frontend; npm run dev"
    Write-Host "  3. Health:   http://localhost:9086/api/v1/health"
    Write-Host "  4. MinIO UI: http://localhost:9001  (minioadmin / minioadmin)"
} finally {
    Pop-Location
}

# Noto 本地 PostgreSQL（pgvector）
# 推荐改用：.\scripts\dev-up.ps1（同时启动 MinIO）
# 本脚本仅启动数据库，等价于 docker compose up -d db

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

Write-Host "Tip: use .\scripts\dev-up.ps1 for db + minio together." -ForegroundColor Gray

if (-not (Test-Path (Join-Path $root ".env"))) {
    Copy-Item (Join-Path $root ".env.example") (Join-Path $root ".env")
}

Push-Location $root
try {
    docker compose up -d db
    docker exec noto-db psql -U postgres -d noto_zhihui_dev -c "CREATE EXTENSION IF NOT EXISTS vector;" 2>$null | Out-Null
    docker ps --filter "name=noto-db" --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"
    Write-Host ""
    Write-Host "JDBC: jdbc:postgresql://localhost:5432/noto_zhihui_dev (user postgres, see .env)"
} finally {
    Pop-Location
}

# 清除演示数据种子标记，下次 backend 启动时会重新写入 v2 演示数据
# 用法：.\scripts\reset-demo-seed.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

$sql = "DELETE FROM user_setting WHERE setting_key='noto.demo.seeded';"

Write-Host "== Reset demo seed marker ==" -ForegroundColor Cyan

# 优先 Docker db 容器
$dockerDb = docker ps --filter "name=noto-db" --format "{{.Names}}" 2>$null | Select-Object -First 1
if ($dockerDb) {
    docker exec $dockerDb psql -U postgres -d noto_zhihui_dev -c $sql | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Cleared seed marker via Docker ($dockerDb)." -ForegroundColor Green
        Write-Host "Restart backend and look for: Demo data v3 seeded" -ForegroundColor Gray
        exit 0
    }
}

# 本地 PostgreSQL
$psql = Get-Command psql -ErrorAction SilentlyContinue
if ($psql) {
    $env:PGPASSWORD = "123456"
    & psql -h localhost -U postgres -d noto_zhihui_dev -c $sql
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Cleared seed marker via local psql." -ForegroundColor Green
        Write-Host "Restart backend and look for: Demo data v3 seeded" -ForegroundColor Gray
        exit 0
    }
}

Write-Host "Could not connect to database. Start db first:" -ForegroundColor Yellow
Write-Host "  docker compose up -d db" -ForegroundColor Gray
Write-Host "Or run SQL manually:" -ForegroundColor Gray
Write-Host "  $sql" -ForegroundColor DarkGray
exit 1

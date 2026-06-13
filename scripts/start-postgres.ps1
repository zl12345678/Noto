# Noto 本地 PostgreSQL（pgvector）
# 推荐：.\scripts\dev-up.ps1（同时启动 MinIO）
# 本脚本仅启动数据库，等价于 docker compose up -d db

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib\Noto-Docker.ps1')

Write-Host 'Tip: use .\scripts\dev-up.ps1 for db + minio together.' -ForegroundColor Gray

Ensure-NotoEnvFile

Invoke-InNotoRoot {
    Start-NotoComposeServices @('db')
    Wait-NotoContainerHealthy -ContainerName 'noto-db' -TimeoutSeconds 30 | Out-Null
    Initialize-NotoPgvector

    $db = Get-NotoDatabaseName
    $user = Get-NotoPostgresUser
    Show-NotoContainers
    Write-Host ''
    Write-Host "JDBC: jdbc:postgresql://localhost:5432/$db (user $user, password see .env)"
}

# 停止 Noto 演示栈（所有 compose 服务，保留数据卷）
# 用法：.\scripts\demo-down.ps1

param(
    [switch]$RemoveVolumes
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib\Noto-Docker.ps1')

Write-Host '== Stop Noto demo stack ==' -ForegroundColor Cyan

Invoke-InNotoRoot {
    if ($RemoveVolumes) {
        Write-Host 'WARNING: this removes database and MinIO volumes.' -ForegroundColor Yellow
        docker compose down -v
    } else {
        docker compose down
    }
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host 'All noto compose services stopped.' -ForegroundColor Green
}

# 停止生产 Compose 栈（保留数据卷）
# 用法：.\scripts\prod-down.ps1
#       .\scripts\prod-down.ps1 -RemoveVolumes

param(
    [switch]$RemoveVolumes
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib\Noto-Docker.ps1')

Write-Host '== Stop Noto production stack ==' -ForegroundColor Cyan

Invoke-InNotoRoot {
    if ($RemoveVolumes) {
        Write-Host 'WARNING: removing postgres and minio volumes.' -ForegroundColor Yellow
        Invoke-NotoProdCompose @('down', '-v')
    } else {
        Invoke-NotoProdCompose @('down')
    }
    Write-Host 'Production stack stopped.' -ForegroundColor Green
}

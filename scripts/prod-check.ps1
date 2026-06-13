# 生产环境 .env 上线前检查
# 用法：.\scripts\prod-check.ps1
#       .\scripts\prod-check.ps1 -Strict   # 有问题时 exit 1

param(
    [switch]$Strict
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib\Noto-Docker.ps1')

Write-Host '== Noto production preflight ==' -ForegroundColor Cyan

Ensure-NotoProdEnvFile
$ok = Test-NotoProdEnv -Strict:$Strict

if ($ok) {
    Write-Host ''
    Write-Host 'Preflight passed.' -ForegroundColor Green
    exit 0
}

Write-Host ''
Write-Host 'Preflight failed — fix .env before production deploy.' -ForegroundColor Red
Write-Host 'Template: deploy/env.prod.example' -ForegroundColor Gray
exit 1

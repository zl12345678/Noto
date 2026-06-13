# 生产环境拉代码并重建（适用于已 clone 到服务器的 Windows 环境）
# Linux 云服务器请用：deploy/prod-update.sh
# 用法：.\scripts\prod-update.ps1

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib\Noto-Docker.ps1')

Write-Host '== Noto production update ==' -ForegroundColor Cyan

Invoke-InNotoRoot {
    if (Test-Path (Join-Path $script:NotoRoot '.git')) {
        git pull
        if ($LASTEXITCODE -ne 0) { throw 'git pull failed' }
    } else {
        Write-Host 'Not a git repo — skip pull.' -ForegroundColor Yellow
    }

    if (-not (Test-NotoProdEnv -Strict)) {
        Write-Host 'Aborting update due to .env issues.' -ForegroundColor Red
        exit 1
    }

    Invoke-NotoProdCompose @('up', '-d', '--build')
    Write-Host 'Update complete. Check: docker compose logs -f backend' -ForegroundColor Green
}

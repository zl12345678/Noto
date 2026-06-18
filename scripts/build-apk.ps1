# Noto · 知微 — UniApp 移动端构建

param(
    [switch]$H5Only,
    [switch]$InstallDeps
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$MobileDir = Join-Path $Root 'mobile'

if (-not (Test-Path $MobileDir)) {
    throw "未找到 mobile 目录: $MobileDir"
}

Write-Host '== Noto UniApp ==' -ForegroundColor Cyan
Write-Host "目录: $MobileDir" -ForegroundColor DarkGray

Push-Location $MobileDir
try {
    if ($InstallDeps -or -not (Test-Path 'node_modules')) {
        Write-Host '== npm install ==' -ForegroundColor Cyan
        npm install
        if ($LASTEXITCODE -ne 0) { throw 'npm install 失败' }
    }

    if ($H5Only) {
        Write-Host '== 构建 H5 ==' -ForegroundColor Cyan
        npm run build:h5
    } else {
        Write-Host '== 构建 App-Android 资源 ==' -ForegroundColor Cyan
        npm run build:app-android
    }

    if ($LASTEXITCODE -ne 0) { throw 'uni-app 构建失败' }

    Write-Host ''
    Write-Host 'BUILD SUCCESSFUL' -ForegroundColor Green
    Write-Host '产物: mobile\dist\' -ForegroundColor Green
    Write-Host 'Android 安装包: 用 HBuilderX 打开 mobile → 发行 → 原生 App-云打包' -ForegroundColor DarkGray
    Write-Host '详见 docs/UNIAPP.md' -ForegroundColor DarkGray
} finally {
    Pop-Location
}

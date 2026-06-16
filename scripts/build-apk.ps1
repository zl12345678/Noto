# Noto · 知微 — Android APK 一键打包（Capacitor）
# 用法：
#   .\scripts\build-apk.ps1                 # Debug APK（Web 构建 + cap sync + Gradle）
#   .\scripts\build-apk.ps1 -SkipWeb        # 仅 Gradle（Web 未改时）
#   .\scripts\build-apk.ps1 -Release        # Release APK（需 keystore，见 docs/BUILD_APK.md）
#   .\scripts\build-apk.ps1 -Install        # 打包后 adb 安装到已连接手机
#   .\scripts\build-apk.ps1 -SetupSdk         # 首次：从腾讯镜像安装 Android SDK
#   .\scripts\build-apk.ps1 -SetupSdk -SkipWeb -Install
#
# 说明：
#   - 后端 Spring Boot 仍用 JDK 17；Android 构建需要 Java 21（Android Studio JBR）
#   - 发版前请编辑 frontend/.env.capacitor 中的 VITE_API_BASE_URL
#   - 详见 docs/BUILD_APK.md

param(
    [switch]$SkipWeb,
    [switch]$Release,
    [switch]$Install,
    [switch]$SetupSdk
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib\Noto-Android.ps1')

Write-Host '== Noto Android APK ==' -ForegroundColor Cyan
Write-Host "项目根目录: $(Get-NotoRoot)" -ForegroundColor DarkGray

$envInfo = Initialize-NotoAndroidBuildEnv -SetupSdk:$SetupSdk

if (-not $SkipWeb) {
    Invoke-NotoCapSync -FrontendDir $envInfo.FrontendDir
} else {
    Write-Host '跳过 Web 构建与 cap sync（-SkipWeb）' -ForegroundColor Yellow
}

$variant = if ($Release) { 'Release' } else { 'Debug' }
$apkPath = Invoke-NotoGradleAssemble -Variant $variant -AndroidDir $envInfo.AndroidDir | Select-Object -Last 1

Write-Host ''
Write-Host 'BUILD SUCCESSFUL' -ForegroundColor Green
Write-Host "APK: $apkPath" -ForegroundColor Green
Write-Host "API: 见 frontend/.env.capacitor -> VITE_API_BASE_URL" -ForegroundColor DarkGray

if ($Install) {
    Install-NotoApk -ApkPath $apkPath
    Write-Host '已安装到设备' -ForegroundColor Green
}

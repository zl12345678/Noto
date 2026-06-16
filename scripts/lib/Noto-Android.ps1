# Shared helpers for Noto Android / Capacitor APK builds.
# Dot-source: . (Join-Path $PSScriptRoot 'lib\Noto-Android.ps1')

$script:NotoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$script:TencentSdkMirror = 'https://mirrors.cloud.tencent.com/AndroidSDK'

function Get-NotoRoot {
    return $script:NotoRoot
}

function Get-NotoFrontendDir {
    return Join-Path $script:NotoRoot 'frontend'
}

function Get-NotoAndroidDir {
    return Join-Path (Get-NotoFrontendDir) 'android'
}

function Find-AndroidStudioJbr {
    $candidates = @(
        $env:NOTO_ANDROID_JBR
        $env:JAVA_HOME
        'D:\Android Studio\jbr'
        (Join-Path ${env:ProgramFiles} 'Android\Android Studio\jbr')
        (Join-Path ${env:LOCALAPPDATA} 'Programs\Android Studio\jbr')
    ) | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin\java.exe')) }

    foreach ($path in $candidates) {
        $prev = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $version = & (Join-Path $path 'bin\java.exe') -version 2>&1 | Out-String
        } finally {
            $ErrorActionPreference = $prev
        }
        if ($version -match 'version "21') {
            return (Resolve-Path $path).Path
        }
    }

    foreach ($path in $candidates) {
        return (Resolve-Path $path).Path
    }

    return $null
}

function Get-DefaultAndroidSdkRoot {
    if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
        return (Resolve-Path $env:ANDROID_HOME).Path
    }
    if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) {
        return (Resolve-Path $env:ANDROID_SDK_ROOT).Path
    }
    $default = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path $default) {
        return (Resolve-Path $default).Path
    }
    return $default
}

function Read-AndroidSdkFromLocalProperties {
    param([string]$AndroidDir = (Get-NotoAndroidDir))

    $localProps = Join-Path $AndroidDir 'local.properties'
    if (-not (Test-Path $localProps)) { return $null }

    foreach ($line in Get-Content $localProps -Encoding UTF8) {
        if ($line -match '^\s*sdk\.dir=(.+)$') {
            $raw = $Matches[1].Trim() -replace '\\:', ':' -replace '\\\\', '\\'
            if (Test-Path $raw) {
                return (Resolve-Path $raw).Path
            }
            return $raw
        }
    }
    return $null
}

function Convert-ToGradlePropertyPath {
    param([Parameter(Mandatory = $true)][string]$Path)
    return ($Path -replace '\\', '\\')
}

function Ensure-AndroidLocalProperties {
    param(
        [Parameter(Mandatory = $true)][string]$SdkRoot,
        [string]$AndroidDir = (Get-NotoAndroidDir)
    )

    $localProps = Join-Path $AndroidDir 'local.properties'
    $escaped = Convert-ToGradlePropertyPath -Path $SdkRoot
    $content = "sdk.dir=$escaped`n"

    if (Test-Path $localProps) {
        $existing = Get-Content $localProps -Raw -Encoding UTF8
        if ($existing -match '^\s*sdk\.dir=') {
            $updated = [regex]::Replace($existing, '(?m)^\s*sdk\.dir=.*$', "sdk.dir=$escaped")
            Set-Content -Path $localProps -Value ($updated.TrimEnd() + "`n") -Encoding UTF8 -NoNewline
            return
        }
    }

    Set-Content -Path $localProps -Value $content -Encoding UTF8 -NoNewline
}

function Ensure-GradleJavaHome {
    param(
        [Parameter(Mandatory = $true)][string]$JbrPath,
        [string]$AndroidDir = (Get-NotoAndroidDir)
    )

    $gradleProps = Join-Path $AndroidDir 'gradle.properties'
    if (-not (Test-Path $gradleProps)) { return }

    $escaped = Convert-ToGradlePropertyPath -Path $JbrPath
    $line = "org.gradle.java.home=$escaped"
    $content = Get-Content $gradleProps -Raw -Encoding UTF8

    if ($content -match '(?m)^org\.gradle\.java\.home=') {
        $content = [regex]::Replace($content, '(?m)^org\.gradle\.java\.home=.*$', $line)
    } else {
        $content = $content.TrimEnd() + "`n`n# Capacitor Android 需要 Java 21（Android Studio JBR）`n$line`n"
    }

    Set-Content -Path $gradleProps -Value $content -Encoding UTF8 -NoNewline
}

function Test-AndroidSdkReady {
    param(
        [Parameter(Mandatory = $true)][string]$SdkRoot,
        [int]$PlatformApi = 35
    )

    $missing = @()
    if (-not (Test-Path (Join-Path $SdkRoot 'platform-tools\adb.exe'))) {
        $missing += 'platform-tools'
    }
    if (-not (Test-Path (Join-Path $SdkRoot "platforms\android-$PlatformApi\android.jar"))) {
        $missing += "platforms;android-$PlatformApi"
    }
    if (-not (Test-Path (Join-Path $SdkRoot 'build-tools\34.0.0\aapt.exe'))) {
        $missing += 'build-tools;34.0.0'
    }
    $ready = ($missing.Count -eq 0)
    return [PSCustomObject]@{
        Ready = $ready
        Missing = $missing
    }
}

function Install-AndroidSdkPackageFromMirror {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][string]$DestDir,
        [string]$ExpectedSubDir = $null
    )

    $zip = Join-Path $env:TEMP ("noto-sdk-" + [Guid]::NewGuid().ToString('N') + '.zip')
    $extract = Join-Path $env:TEMP ("noto-sdk-" + [Guid]::NewGuid().ToString('N'))

    try {
        Write-Host "  下载 $Url" -ForegroundColor DarkGray
        curl.exe -L --connect-timeout 30 --max-time 900 -o $zip $Url
        if ($LASTEXITCODE -ne 0) { throw "下载失败: $Url" }

        New-Item -ItemType Directory -Force -Path $DestDir | Out-Null
        Expand-Archive -Path $zip -DestinationPath $extract -Force

        $inner = Get-ChildItem $extract | Select-Object -First 1
        if (-not $inner) { throw "压缩包为空: $Url" }

        $target = if ($ExpectedSubDir) { Join-Path $DestDir $ExpectedSubDir } else { $DestDir }
        if (Test-Path $target) { Remove-Item -Recurse -Force $target }
        if ($ExpectedSubDir) {
            New-Item -ItemType Directory -Force -Path $DestDir | Out-Null
            Move-Item $inner.FullName $target
        } else {
            Move-Item $inner.FullName $target
        }
    } finally {
        Remove-Item $zip -Force -ErrorAction SilentlyContinue
        Remove-Item $extract -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Install-AndroidCmdlineTools {
    param([Parameter(Mandatory = $true)][string]$SdkRoot)

    $toolsDir = Join-Path $SdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
    if (Test-Path $toolsDir) { return }

    $url = "$script:TencentSdkMirror/commandlinetools-win-11076708_latest.zip"
    $extract = Join-Path $env:TEMP ("noto-cmdline-" + [Guid]::NewGuid().ToString('N'))
    $zip = Join-Path $env:TEMP ("noto-cmdline-" + [Guid]::NewGuid().ToString('N') + '.zip')

    try {
        Write-Host '  安装 cmdline-tools ...' -ForegroundColor DarkGray
        curl.exe -L --connect-timeout 30 --max-time 900 -o $zip $url
        if ($LASTEXITCODE -ne 0) { throw 'cmdline-tools 下载失败' }

        Expand-Archive -Path $zip -DestinationPath $extract -Force
        New-Item -ItemType Directory -Force -Path (Join-Path $SdkRoot 'cmdline-tools') | Out-Null
        $dest = Join-Path $SdkRoot 'cmdline-tools\latest'
        if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
        Move-Item (Join-Path $extract 'cmdline-tools') $dest
    } finally {
        Remove-Item $zip -Force -ErrorAction SilentlyContinue
        Remove-Item $extract -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Install-NotoAndroidSdk {
    param(
        [string]$SdkRoot = (Get-DefaultAndroidSdkRoot),
        [int]$PlatformApi = 35
    )

    Write-Host '== 安装 / 补全 Android SDK（腾讯镜像）==' -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path $SdkRoot | Out-Null
    Install-AndroidCmdlineTools -SdkRoot $SdkRoot

    if (-not (Test-Path (Join-Path $SdkRoot 'platform-tools\adb.exe'))) {
        Install-AndroidSdkPackageFromMirror `
            -Url "$script:TencentSdkMirror/platform-tools_r35.0.2-win.zip" `
            -DestDir $SdkRoot
    }

    if (-not (Test-Path (Join-Path $SdkRoot "platforms\android-$PlatformApi\android.jar"))) {
        New-Item -ItemType Directory -Force -Path (Join-Path $SdkRoot 'platforms') | Out-Null
        Install-AndroidSdkPackageFromMirror `
            -Url "$script:TencentSdkMirror/platform-${PlatformApi}_r02.zip" `
            -DestDir (Join-Path $SdkRoot 'platforms')
    }

    if (-not (Test-Path (Join-Path $SdkRoot 'build-tools\34.0.0\aapt.exe'))) {
        New-Item -ItemType Directory -Force -Path (Join-Path $SdkRoot 'build-tools') | Out-Null
        Install-AndroidSdkPackageFromMirror `
            -Url "$script:TencentSdkMirror/build-tools_r34-windows.zip" `
            -DestDir (Join-Path $SdkRoot 'build-tools') `
            -ExpectedSubDir '34.0.0'
    }

    if (-not (Test-Path (Join-Path $SdkRoot 'build-tools\35.0.0\aapt.exe'))) {
        Install-AndroidSdkPackageFromMirror `
            -Url "$script:TencentSdkMirror/build-tools_r35_windows.zip" `
            -DestDir (Join-Path $SdkRoot 'build-tools') `
            -ExpectedSubDir '35.0.0'
    }

    $sdkStatus = Test-AndroidSdkReady -SdkRoot $sdkRoot -PlatformApi $PlatformApi
    if (-not $sdkStatus.Ready) {
        throw "SDK 仍缺少组件: $($sdkStatus.Missing -join ', ')"
    }

    Write-Host "SDK 就绪: $SdkRoot" -ForegroundColor Green
    return $SdkRoot
}

function Initialize-NotoAndroidBuildEnv {
    param(
        [switch]$SetupSdk,
        [int]$PlatformApi = 35
    )

    $androidDir = Get-NotoAndroidDir
    if (-not (Test-Path $androidDir)) {
        throw @"
未找到 frontend/android。请先在 frontend 目录执行：
  npm install
  npx cap add android
"@
    }

    $jbr = Find-AndroidStudioJbr
    if (-not $jbr) {
        throw @"
未找到 Java 21（Android Studio JBR）。
请安装 Android Studio，或设置环境变量 NOTO_ANDROID_JBR 指向 JBR 目录。
后端仍可使用 JDK 17；仅 Android Gradle 构建需要 Java 21。
"@
    }

    $sdkRoot = Read-AndroidSdkFromLocalProperties
    if (-not $sdkRoot) { $sdkRoot = Get-DefaultAndroidSdkRoot }

    $sdkStatus = Test-AndroidSdkReady -SdkRoot $sdkRoot -PlatformApi $PlatformApi
    if (-not $sdkStatus.Ready) {
        if ($SetupSdk) {
            $sdkRoot = Install-NotoAndroidSdk -SdkRoot $sdkRoot -PlatformApi $PlatformApi
        } else {
            throw @"
Android SDK 不完整，缺少: $($sdkStatus.Missing -join ', ')
首次请运行: .\scripts\build-apk.ps1 -SetupSdk
或手动安装 SDK 后配置 frontend/android/local.properties
"@
        }
    }

    Ensure-AndroidLocalProperties -SdkRoot $sdkRoot -AndroidDir $androidDir
    Ensure-GradleJavaHome -JbrPath $jbr -AndroidDir $androidDir

    $env:ANDROID_HOME = $sdkRoot
    $env:ANDROID_SDK_ROOT = $sdkRoot
    $env:JAVA_HOME = $jbr
    $env:Path = "$env:JAVA_HOME\bin;$sdkRoot\platform-tools;$env:Path"

    return [PSCustomObject]@{
        AndroidDir = $androidDir
        FrontendDir = Get-NotoFrontendDir
        SdkRoot = $sdkRoot
        JavaHome = $jbr
    }
}

function Invoke-NotoCapSync {
    param([string]$FrontendDir = (Get-NotoFrontendDir))

    Push-Location $FrontendDir
    try {
        Write-Host '== 构建 Web 资源 (build:cap) ==' -ForegroundColor Cyan
        npm run build:cap
        if ($LASTEXITCODE -ne 0) { throw 'npm run build:cap 失败' }

        Write-Host '== 同步 Capacitor (cap sync android) ==' -ForegroundColor Cyan
        npx cap sync android
        if ($LASTEXITCODE -ne 0) { throw 'npx cap sync android 失败' }
    } finally {
        Pop-Location
    }
}

function Invoke-NotoGradleAssemble {
    param(
        [ValidateSet('Debug', 'Release')]
        [string]$Variant = 'Debug',
        [string]$AndroidDir = (Get-NotoAndroidDir)
    )

    $task = if ($Variant -eq 'Release') { 'assembleRelease' } else { 'assembleDebug' }
    $apkRel = if ($Variant -eq 'Release') {
        'app\build\outputs\apk\release\app-release.apk'
    } else {
        'app\build\outputs\apk\debug\app-debug.apk'
    }

    Push-Location $AndroidDir
    try {
        Write-Host "== Gradle $task ==" -ForegroundColor Cyan
        & .\gradlew.bat --stop 2>&1 | Out-Null
        & .\gradlew.bat $task 2>&1 | Out-Host
        if ($LASTEXITCODE -ne 0) { throw "gradlew $task 失败" }
    } finally {
        Pop-Location
    }

    $apkPath = Join-Path $AndroidDir $apkRel
    if (-not (Test-Path $apkPath)) {
        throw "未找到 APK: $apkPath"
    }

    Write-Output (Resolve-Path $apkPath).Path
}

function Install-NotoApk {
    param([Parameter(Mandatory = $true)][string]$ApkPath)

    $adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
    if (-not (Test-Path $adb)) { throw "未找到 adb: $adb" }

    Write-Host '== 安装到设备 (adb install) ==' -ForegroundColor Cyan
    & $adb devices
    & $adb install -r $ApkPath
    if ($LASTEXITCODE -ne 0) { throw 'adb install 失败（请确认 USB 调试已开启）' }
}

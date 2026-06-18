# Noto backend 本地开发启动
# 用法：
#   .\dev.ps1                  # 启动 Spring Boot（dev profile）
#   .\dev.ps1 -WatchCompile    # 启动 + 监听 Java 变更自动 compile（DevTools 热重载）
#   .\dev.ps1 -Debug            # 启用 JDWP 调试端口 5005
#   .\dev.ps1 -NoDemo          # 不写入演示种子数据

param(
    [switch]$WatchCompile,
    [switch]$Debug,
    [switch]$NoDemo
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Find-Jdk17 {
    $candidates = @(
        $env:NOTO_JAVA_HOME
        $env:JAVA_HOME
        'C:\Program Files\Java\jdk-17'
        'C:\Program Files\Eclipse Adoptium\jdk-17.0.6.10-hotspot'
    ) | Where-Object { $_ }

    foreach ($path in $candidates) {
        $java = Join-Path $path 'bin\java.exe'
        if (Test-Path $java) {
            $prev = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                $ver = & $java -version 2>&1 | Out-String
            } finally {
                $ErrorActionPreference = $prev
            }
            if ($ver -match 'version "17') {
                return (Resolve-Path $path).Path
            }
        }
    }

    foreach ($path in $candidates) {
        $java = Join-Path $path 'bin\java.exe'
        if (Test-Path $java) { return (Resolve-Path $path).Path }
    }
    return $null
}

function Stop-BackendOnPort9086 {
    $conn = Get-NetTCPConnection -LocalPort 9086 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $conn) { return }
    Write-Host "Port 9086 in use by PID $($conn.OwningProcess) — stopping..." -ForegroundColor Yellow
    Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
}

function Start-WatchCompile {
    param([string]$BackendRoot)

    $action = {
        $root = $Event.MessageData
        Start-Sleep -Milliseconds 800
        Set-Location $root
        Write-Host '[watch] Java changed — compiling...' -ForegroundColor DarkCyan
        & (Join-Path $root 'mvnw.cmd') -q compile -DskipTests 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $trigger = Join-Path $root '.reloadtrigger'
            if (-not (Test-Path $trigger)) {
                New-Item -ItemType File -Path $trigger -Force | Out-Null
            } else {
                (Get-Item $trigger).LastWriteTime = Get-Date
            }
        } else {
            Write-Host '[watch] compile failed' -ForegroundColor Red
        }
    }

    $watcher = New-Object System.IO.FileSystemWatcher
    $watcher.Path = Join-Path $BackendRoot 'src\main\java'
    $watcher.IncludeSubdirectories = $true
    $watcher.Filter = '*.java'
    $watcher.EnableRaisingEvents = $true

    Register-ObjectEvent -InputObject $watcher -EventName Changed -Action $action -MessageData $BackendRoot | Out-Null
    Register-ObjectEvent -InputObject $watcher -EventName Created -Action $action -MessageData $BackendRoot | Out-Null
    Register-ObjectEvent -InputObject $watcher -EventName Renamed -Action $action -MessageData $BackendRoot | Out-Null
    Write-Host "Watching $($watcher.Path) for changes..." -ForegroundColor Gray
}

$jdk = Find-Jdk17
if (-not $jdk) {
    throw '未找到 JDK 17。请安装 JDK 17 或设置环境变量 JAVA_HOME / NOTO_JAVA_HOME'
}

$env:JAVA_HOME = $jdk
$env:SPRING_PROFILES_ACTIVE = 'dev'
if ($NoDemo) {
    $env:NOTO_DEMO_ENABLED = 'false'
} else {
    $env:NOTO_DEMO_ENABLED = 'true'
}

Stop-BackendOnPort9086

Write-Host "JAVA_HOME=$jdk" -ForegroundColor DarkGray
Write-Host 'Starting backend (dev) on http://localhost:9086 ...' -ForegroundColor Cyan

if ($WatchCompile) {
    Start-WatchCompile -BackendRoot $PSScriptRoot
}

$mvnArgs = @('spring-boot:run')
if ($Debug) {
    $mvnArgs += '-Plocal-debug'
    Write-Host 'Debug attach: localhost:5005' -ForegroundColor Gray
}

& .\mvnw.cmd @mvnArgs
exit $LASTEXITCODE

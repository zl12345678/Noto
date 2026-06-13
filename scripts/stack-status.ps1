# 查看 Noto 容器与关键端口/健康检查状态
# 用法：.\scripts\stack-status.ps1

$ErrorActionPreference = 'Continue'
. (Join-Path $PSScriptRoot 'lib\Noto-Docker.ps1')

Write-Host '== Noto stack status ==' -ForegroundColor Cyan
Write-Host ''

$containers = docker ps -a --filter 'name=noto-' --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
if ($containers) {
    $containers
} else {
    Write-Host 'No noto-* containers found.' -ForegroundColor Gray
}

Write-Host ''
Write-Host 'Local listeners:' -ForegroundColor Cyan
foreach ($port in @(5432, 8080, 9086, 9000, 9001, 5173)) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($conn) {
        Write-Host ("  :{0,-5} PID {1}" -f $port, $conn.OwningProcess) -ForegroundColor Green
    } else {
        Write-Host ("  :{0,-5} (not listening)" -f $port) -ForegroundColor DarkGray
    }
}

Write-Host ''
Write-Host 'Health checks:' -ForegroundColor Cyan
try {
    $health = Invoke-RestMethod -Uri 'http://localhost:9086/api/v1/health' -TimeoutSec 2
    if ($health.code -eq 0) {
        Write-Host '  Backend API: OK' -ForegroundColor Green
    } else {
        Write-Host '  Backend API: unexpected response' -ForegroundColor Yellow
    }
} catch {
    Write-Host '  Backend API: unreachable' -ForegroundColor DarkGray
}

try {
    $frontend = Invoke-WebRequest -Uri 'http://localhost:8080/' -UseBasicParsing -TimeoutSec 2
    if ($frontend.StatusCode -eq 200) {
        Write-Host '  Docker frontend (:8080): OK' -ForegroundColor Green
    }
} catch {
    Write-Host '  Docker frontend (:8080): unreachable' -ForegroundColor DarkGray
}

try {
    $vite = Invoke-WebRequest -Uri 'http://localhost:5173/' -UseBasicParsing -TimeoutSec 2
    if ($vite.StatusCode -eq 200) {
        Write-Host '  Vite dev (:5173): OK' -ForegroundColor Green
    }
} catch {
    Write-Host '  Vite dev (:5173): unreachable' -ForegroundColor DarkGray
}

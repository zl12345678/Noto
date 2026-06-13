# 从运行中的后端导出 OpenAPI 规范到 docs/openapi.yaml
# 用法：.\scripts\export-openapi.ps1
# 前提：后端已启动（默认 http://localhost:9086，profile=dev 启用 springdoc）

param(
    [string]$BaseUrl = 'http://localhost:9086',
    [string]$OutFile = ''
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib\Noto-Docker.ps1')

if (-not $OutFile) {
    $OutFile = Join-Path $script:NotoRoot 'docs\openapi.yaml'
}

$url = "$BaseUrl/v3/api-docs.yaml"
Write-Host "Fetching $url ..." -ForegroundColor Cyan

try {
    Invoke-WebRequest -Uri $url -UseBasicParsing -OutFile $OutFile
} catch {
    Write-Host 'Failed. Ensure backend is running with dev profile (springdoc enabled).' -ForegroundColor Yellow
    Write-Host '  cd backend; .\dev.ps1 -WatchCompile' -ForegroundColor Gray
    Write-Host '  or VS Code task: backend: dev' -ForegroundColor Gray
    throw
}

Write-Host "Written: $OutFile" -ForegroundColor Green
Write-Host "Swagger UI: $BaseUrl/swagger-ui.html" -ForegroundColor Gray

# 从运行中的后端导出 OpenAPI 规范到 docs/openapi.yaml
# 用法：.\scripts\export-openapi.ps1
# 前提：后端已启动（默认 http://localhost:9086，profile=dev 启用 springdoc）

param(
    [string]$BaseUrl = "http://localhost:9086",
    [string]$OutFile = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
if (-not $OutFile) {
    $OutFile = Join-Path $root "docs\openapi.yaml"
}

$url = "$BaseUrl/v3/api-docs.yaml"
Write-Host "Fetching $url ..." -ForegroundColor Cyan

try {
    Invoke-WebRequest -Uri $url -UseBasicParsing -OutFile $OutFile
} catch {
    Write-Host "Failed. Ensure backend is running with dev profile (springdoc enabled)." -ForegroundColor Yellow
    Write-Host "  cd backend; .\mvnw.cmd spring-boot:run" -ForegroundColor Gray
    throw
}

Write-Host "Written: $OutFile" -ForegroundColor Green
Write-Host "Swagger UI: $BaseUrl/swagger-ui.html" -ForegroundColor Gray

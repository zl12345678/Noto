# Shared helpers for Noto · 知微 PowerShell scripts.
# Dot-source from scripts/*.ps1:  . (Join-Path $PSScriptRoot 'lib\Noto-Docker.ps1')

$script:NotoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent

function Get-NotoRoot {
    return $script:NotoRoot
}

function Read-NotoEnvValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Key,
        [string]$Default = ''
    )
    $envFile = Join-Path $script:NotoRoot '.env'
    if (-not (Test-Path $envFile)) { return $Default }
    foreach ($line in Get-Content $envFile -Encoding UTF8) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        if ($trimmed -match '^\s*([^=]+)=(.*)$') {
            if ($Matches[1].Trim() -eq $Key) {
                return $Matches[2].Trim().Trim('"').Trim("'")
            }
        }
    }
    return $Default
}

function Get-NotoDatabaseName {
    $name = Read-NotoEnvValue -Key 'POSTGRES_DB' -Default 'noto_zhihui_dev'
    if ($name) { return $name }
    return 'noto_zhihui_dev'
}

function Get-NotoPostgresUser {
    $user = Read-NotoEnvValue -Key 'POSTGRES_USER' -Default 'postgres'
    if ($user) { return $user }
    return 'postgres'
}

function Get-NotoPostgresPassword {
    $password = Read-NotoEnvValue -Key 'POSTGRES_PASSWORD' -Default '123456'
    if ($password) { return $password }
    return '123456'
}

function Ensure-NotoEnvFile {
    $envFile = Join-Path $script:NotoRoot '.env'
    $exampleFile = Join-Path $script:NotoRoot '.env.example'
    if (-not (Test-Path $envFile)) {
        if (-not (Test-Path $exampleFile)) {
            throw ".env.example not found at $exampleFile"
        }
        Copy-Item $exampleFile $envFile
        Write-Host 'Created .env from .env.example' -ForegroundColor Yellow
    }
}

function Invoke-InNotoRoot {
    param(
        [Parameter(Mandatory = $true)]
        [scriptblock]$ScriptBlock
    )
    Push-Location $script:NotoRoot
    try {
        & $ScriptBlock
    } finally {
        Pop-Location
    }
}

function Start-NotoComposeServices {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Services
    )
    docker compose up -d @Services
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed for: $($Services -join ', ')"
    }
}

function Wait-NotoContainerHealthy {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ContainerName,
        [int]$TimeoutSeconds = 30
    )
    Write-Host "Waiting for $ContainerName..." -ForegroundColor Gray
    for ($i = 0; $i -lt $TimeoutSeconds; $i++) {
        $health = docker inspect -f '{{.State.Health.Status}}' $ContainerName 2>$null
        if ($health -eq 'healthy') {
            return $true
        }
        $running = docker inspect -f '{{.State.Running}}' $ContainerName 2>$null
        if ($running -eq 'true' -and -not $health) {
            return $true
        }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Initialize-NotoPgvector {
    param(
        [string]$ContainerName = 'noto-db',
        [string]$DatabaseName = (Get-NotoDatabaseName),
        [string]$PostgresUser = (Get-NotoPostgresUser)
    )
    $image = docker inspect -f '{{.Config.Image}}' $ContainerName 2>$null
    if (-not $image) {
        Write-Host "Container $ContainerName not found — skip pgvector check." -ForegroundColor Yellow
        return
    }

    Write-Host "PostgreSQL image: $image"
    if ($image -notmatch 'pgvector') {
        Write-Host 'WARNING: not using pgvector image — RAG will fall back to in-memory scan.' -ForegroundColor Yellow
        Write-Host 'Fix: docker compose down db; docker rm -f noto-db; docker compose up -d db'
        return
    }

    docker exec $ContainerName psql -U $PostgresUser -d $DatabaseName -c 'CREATE EXTENSION IF NOT EXISTS vector;' 2>$null | Out-Null
    $ext = docker exec $ContainerName psql -U $PostgresUser -d $DatabaseName -tAc "SELECT extversion FROM pg_extension WHERE extname='vector';" 2>$null
    if ($ext) {
        Write-Host "pgvector extension: $ext" -ForegroundColor Green
    }
}

function Show-NotoContainers {
    docker ps --filter 'name=noto-' --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
}

function Stop-NotoProcessOnPort {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $conn) { return $false }
    $pid = $conn.OwningProcess
    Write-Host "Port $Port is used by PID $pid — stopping..." -ForegroundColor Yellow
    Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    return $true
}

function Wait-NotoBackendHealth {
    param(
        [string]$BaseUrl = 'http://localhost:9086',
        [int]$TimeoutSeconds = 120,
        [int]$IntervalSeconds = 2
    )
    $url = "$BaseUrl/api/v1/health"
    Write-Host "Waiting for backend: $url ..." -ForegroundColor Gray
    $attempts = [math]::Ceiling($TimeoutSeconds / $IntervalSeconds)
    for ($i = 0; $i -lt $attempts; $i++) {
        try {
            $resp = Invoke-RestMethod -Uri $url -TimeoutSec 3
            if ($resp.code -eq 0) {
                Write-Host 'Backend is UP' -ForegroundColor Green
                return $true
            }
        } catch { }
        Start-Sleep -Seconds $IntervalSeconds
    }
    Write-Host "Backend not ready — check logs or local IDE process." -ForegroundColor Yellow
    return $false
}

function Ensure-NotoComposeService {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Service
    )
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = docker compose up -d $Service 2>&1
    $exit = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    if ($exit -ne 0 -or ($output | Out-String) -match 'Error response') {
        Write-Host "Recreating orphan container for $Service ..." -ForegroundColor Yellow
        switch ($Service) {
            'minio' { docker rm -f noto-minio 2>$null | Out-Null }
            'db' { docker rm -f noto-db 2>$null | Out-Null }
            'backend' { docker rm -f noto-backend 2>$null | Out-Null }
            'frontend' { docker rm -f noto-frontend 2>$null | Out-Null }
        }
        docker compose up -d $Service
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to start $Service"
        }
    }
}

function Write-NotoDevNextSteps {
    Write-Host ''
    Write-Host 'Next steps:' -ForegroundColor Cyan
    Write-Host '  1. Backend:  cd backend; .\dev.ps1 -WatchCompile   (or run ZhihuiApplication in IDE)'
    Write-Host '  2. Frontend: cd frontend; npm run dev'
    Write-Host '  3. Health:   http://localhost:9086/api/v1/health'
    Write-Host '  4. MinIO UI: http://localhost:9001  (minioadmin / minioadmin)'
    Write-Host '  5. Stop infra: .\scripts\dev-down.ps1'
}

function Write-NotoDemoBanner {
    Write-Host ''
    Write-Host '========== Demo Ready ==========' -ForegroundColor Cyan
    Write-Host '  Web UI:   http://localhost:8080'
    Write-Host '  API:      http://localhost:9086/api/v1/health'
    Write-Host '  Login:    admin / admin123'
    Write-Host '  MinIO UI: http://localhost:9001  (minioadmin / minioadmin)'
    Write-Host ''
    Write-Host 'Flow: 周会纪要 -> 规则提取待办 -> 首页看板拖拽 -> Ctrl+K 搜 Q2'
    Write-Host 'Stop: .\scripts\demo-down.ps1'
    Write-Host 'Dev mode: .\scripts\dev-up.ps1  (IDE backend + npm run dev)'
}

function Get-NotoProdComposeFiles {
    return @(
        '-f', (Join-Path $script:NotoRoot 'docker-compose.yml'),
        '-f', (Join-Path $script:NotoRoot 'deploy\docker-compose.prod.yml')
    )
}

function Ensure-NotoProdEnvFile {
    $envFile = Join-Path $script:NotoRoot '.env'
    $prodExample = Join-Path $script:NotoRoot 'deploy\env.prod.example'
    if (-not (Test-Path $envFile)) {
        if (-not (Test-Path $prodExample)) {
            throw "Production env template not found: $prodExample"
        }
        Copy-Item $prodExample $envFile
        Write-Host 'Created .env from deploy/env.prod.example — edit secrets before deploy!' -ForegroundColor Yellow
    }
}

function Test-NotoPlaceholderValue {
    param([string]$Value)
    if (-not $Value) { return $true }
    $lower = $Value.ToLowerInvariant()
    if ($lower -match '请改|your_|changeme|example|placeholder|admin123') { return $true }
    if ($lower -match 'noto-zhihui-docker-secret|noto-zhihui-default-secret') { return $true }
    return $false
}

function Test-NotoProdEnv {
    param(
        [switch]$Strict
    )
    $issues = @()
    $warnings = @()

    $envFile = Join-Path $script:NotoRoot '.env'
    if (-not (Test-Path $envFile)) {
        $issues += '.env missing — copy deploy/env.prod.example to .env'
    } else {
        $jwt = Read-NotoEnvValue -Key 'NOTO_JWT_SECRET'
        if ($jwt.Length -lt 32 -or (Test-NotoPlaceholderValue $jwt)) {
            $issues += 'NOTO_JWT_SECRET must be at least 32 random characters (not default/placeholder)'
        }

        $pgPass = Read-NotoEnvValue -Key 'POSTGRES_PASSWORD'
        if ((Test-NotoPlaceholderValue $pgPass) -or $pgPass -eq 'postgres' -or $pgPass -eq '123456') {
            $issues += 'POSTGRES_PASSWORD must be a strong password'
        }

        $minioPass = Read-NotoEnvValue -Key 'MINIO_ROOT_PASSWORD'
        if ((Test-NotoPlaceholderValue $minioPass) -or $minioPass -eq 'minioadmin') {
            $issues += 'MINIO_ROOT_PASSWORD must be changed from default'
        }

        $demo = Read-NotoEnvValue -Key 'NOTO_DEMO_ENABLED' -Default 'false'
        if ($demo -eq 'true') {
            $warnings += 'NOTO_DEMO_ENABLED=true — production should use false'
        }

        $ai = Read-NotoEnvValue -Key 'NOTO_AI_ENABLED' -Default 'false'
        $aiKey = Read-NotoEnvValue -Key 'AI_DASHSCOPE_API_KEY'
        if ($ai -eq 'true' -and -not $aiKey) {
            $warnings += 'NOTO_AI_ENABLED=true but AI_DASHSCOPE_API_KEY is empty'
        }
    }

    foreach ($item in $warnings) {
        Write-Host "WARN: $item" -ForegroundColor Yellow
    }
    foreach ($item in $issues) {
        Write-Host "FAIL: $item" -ForegroundColor Red
    }

    if ($Strict -and $issues.Count -gt 0) {
        return $false
    }
    return $issues.Count -eq 0
}

function Invoke-NotoProdCompose {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$ComposeArgs
    )
    $files = Get-NotoProdComposeFiles
    docker compose @files @ComposeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed: $($ComposeArgs -join ' ')"
    }
}

function Write-NotoProdNextSteps {
    Write-Host ''
    Write-Host '========== Production stack (local bind) ==========' -ForegroundColor Cyan
    Write-Host '  Health:  http://127.0.0.1:8080/api/v1/health'
    Write-Host '  Web UI:  http://127.0.0.1:8080  (bind localhost only)'
    Write-Host ''
    Write-Host 'Cloud server next steps:' -ForegroundColor Cyan
    Write-Host '  1. Configure host Nginx: deploy/nginx/noto.conf'
    Write-Host '  2. HTTPS: certbot --nginx -d your.domain'
    Write-Host '  3. Security group: open only 22/80/443 (not 5432/9000/9086)'
    Write-Host '  4. Linux scripts: deploy/prod-up.sh, deploy/prod-backup.sh'
    Write-Host '  5. Docs: deploy/README.md, docs/DEV_DEPLOY.md §5.7'
    Write-Host ''
    Write-Host 'Stop: .\scripts\prod-down.ps1'
}

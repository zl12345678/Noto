# Noto 本地 MinIO（Docker 单容器，国内镜像源）
# 用法：.\scripts\start-minio.ps1

$ErrorActionPreference = "Stop"

$containerName = "noto-minio"
$imageTag = "RELEASE.2024-12-18T13-15-44Z"
$localImage = "minio/minio:$imageTag"
$apiPort = if ($env:MINIO_API_PORT) { $env:MINIO_API_PORT } else { "9000" }
$consolePort = if ($env:MINIO_CONSOLE_PORT) { $env:MINIO_CONSOLE_PORT } else { "9001" }
$user = if ($env:MINIO_ROOT_USER) { $env:MINIO_ROOT_USER } else { "minioadmin" }
$password = if ($env:MINIO_ROOT_PASSWORD) { $env:MINIO_ROOT_PASSWORD } else { "minioadmin" }

# 国内镜像前缀（按推荐顺序尝试）
$mirrors = @(
    "docker.m.daocloud.io",
    "docker.1panel.live",
    "docker.imgdb.de",
    "docker.hlmirror.com",
    "docker.1ms.run",
    "docker.xuanyuan.me"
)

function Test-LocalImage {
    $found = docker images $localImage --format "{{.Repository}}:{{.Tag}}" 2>$null
    return [bool]$found
}

function Pull-MinioImage {
    if (Test-LocalImage) {
        Write-Host "Local image already exists: $localImage"
        return
    }

    foreach ($mirror in $mirrors) {
        $remote = "${mirror}/minio/minio:${imageTag}"
        Write-Host ""
        Write-Host "Trying mirror: $remote" -ForegroundColor Cyan
        try {
            docker pull $remote
            if ($LASTEXITCODE -ne 0) {
                throw "docker pull exit code $LASTEXITCODE"
            }
            docker tag $remote $localImage
            Write-Host "Pulled and tagged as $localImage" -ForegroundColor Green
            return
        } catch {
            Write-Host "Failed: $_" -ForegroundColor Yellow
        }
    }

    throw @"
All mirrors failed. Try manually:
  docker pull docker.m.daocloud.io/minio/minio:$imageTag
  docker tag docker.m.daocloud.io/minio/minio:$imageTag $localImage

Or set Docker Engine registry-mirrors to:
  https://docker.m.daocloud.io
"@
}

Write-Host "Checking Docker..."
docker version | Out-Null

$existing = docker ps -a --filter "name=^${containerName}$" --format "{{.Names}}"
if ($existing -eq $containerName) {
    $running = docker ps --filter "name=^${containerName}$" --format "{{.Names}}"
    if ($running -eq $containerName) {
        Write-Host "MinIO already running: http://localhost:${consolePort} (user: $user)"
        exit 0
    }
    Write-Host "Starting existing container $containerName..."
    docker start $containerName | Out-Null
} else {
    Pull-MinioImage
    Write-Host "Creating container $containerName ..."
    docker run -d `
        --name $containerName `
        --restart unless-stopped `
        -p "${apiPort}:9000" `
        -p "${consolePort}:9001" `
        -e "MINIO_ROOT_USER=$user" `
        -e "MINIO_ROOT_PASSWORD=$password" `
        -v noto_minio_data:/data `
        $localImage `
        server /data --console-address ":9001"
}

Start-Sleep -Seconds 2
docker ps --filter "name=$containerName" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
Write-Host ""
Write-Host "MinIO API:      http://localhost:$apiPort"
Write-Host "MinIO Console:  http://localhost:$consolePort"
Write-Host "Login:          $user / $password"
Write-Host ""
Write-Host "Backend config (application-dev.yml): noto.minio.enabled=true, endpoint=http://localhost:$apiPort"

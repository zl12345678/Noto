#!/usr/bin/env bash
# 云服务器生产部署（Compose 合并 + localhost 绑定）
# 用法：./deploy/prod-up.sh
#       ./deploy/prod-up.sh --skip-check

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

COMPOSE=(docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml)
SKIP_CHECK=0

for arg in "$@"; do
  case "$arg" in
    --skip-check) SKIP_CHECK=1 ;;
  esac
done

echo "== Noto production deploy =="

if [[ $SKIP_CHECK -eq 0 ]]; then
  bash deploy/prod-check.sh --strict
fi

echo "Building and starting stack..."
"${COMPOSE[@]}" up -d --build

echo "Waiting for health..."
for i in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:8080/api/v1/health | grep -q '"code":0'; then
    echo "Health check: OK"
    break
  fi
  if [[ $i -eq 60 ]]; then
    echo "Health check: pending — docker compose logs -f backend"
  fi
  sleep 2
done

docker ps --filter name=noto- --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'

cat <<'EOF'

========== Production stack ==========
  Health: http://127.0.0.1:8080/api/v1/health

Next steps:
  1. sudo cp deploy/nginx/noto.conf /etc/nginx/sites-available/noto
  2. Replace noto.example.com with your domain
  3. sudo certbot --nginx -d your.domain
  4. Security group: only 22/80/443 open

Stop:  ./deploy/prod-down.sh
Docs:  deploy/README.md

EOF

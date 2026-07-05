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
chmod 644 建表SQL.sql 2>/dev/null || true
"${COMPOSE[@]}" up -d --build

echo "Waiting for health..."
if [[ -f .env ]]; then
  FRONTEND_HOST="${FRONTEND_HOST:-$(sed -n 's/^FRONTEND_HOST=//p' .env | tail -n 1)}"
  FRONTEND_IPV4_HOST="${FRONTEND_IPV4_HOST:-$(sed -n 's/^FRONTEND_IPV4_HOST=//p' .env | tail -n 1)}"
  FRONTEND_IPV6_HOST="${FRONTEND_IPV6_HOST:-$(sed -n 's/^FRONTEND_IPV6_HOST=//p' .env | tail -n 1)}"
  FRONTEND_PORT="${FRONTEND_PORT:-$(sed -n 's/^FRONTEND_PORT=//p' .env | tail -n 1)}"
fi
FRONTEND_PORT="${FRONTEND_PORT:-8080}"
FRONTEND_HEALTH_HOST="127.0.0.1"
case "${FRONTEND_IPV4_HOST:-${FRONTEND_HOST:-127.0.0.1}}" in
  "" | "::" | "[::]")
    FRONTEND_HEALTH_HOST="[::1]"
    ;;
esac
FRONTEND_HEALTH_URL="http://${FRONTEND_HEALTH_HOST}:${FRONTEND_PORT}/api/v1/health"

for i in $(seq 1 60); do
  if curl -g -fsS "$FRONTEND_HEALTH_URL" | grep -q '"code":0'; then
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
  Health: see FRONTEND_IPV4_HOST/FRONTEND_IPV6_HOST/FRONTEND_PORT in .env

Next steps:
  1. sudo cp deploy/nginx/noto.conf /etc/nginx/sites-available/noto
  2. Replace noto.example.com with your domain
  3. sudo certbot --nginx -d your.domain
  4. Security group: only 22/80/443 open

Stop:  ./deploy/prod-down.sh
Docs:  deploy/README.md

EOF

#!/usr/bin/env bash
# 停止生产栈（默认保留数据卷）
# 用法：./deploy/prod-down.sh
#       ./deploy/prod-down.sh --volumes

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

COMPOSE=(docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml)

echo "== Stop Noto production stack =="

if [[ "${1:-}" == "--volumes" ]]; then
  echo "WARNING: removing postgres and minio volumes."
  "${COMPOSE[@]}" down -v
else
  "${COMPOSE[@]}" down
fi

echo "Production stack stopped."

#!/usr/bin/env bash
# PostgreSQL 备份（noto-db 容器）
# 用法：./deploy/prod-backup.sh
#       ./deploy/prod-backup.sh /var/backups/noto

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${1:-$ROOT/backups}"
mkdir -p "$OUT_DIR"

read_env() {
  local key="$1"
  local default="${2:-}"
  local env_file="$ROOT/.env"
  if [[ ! -f "$env_file" ]]; then
    echo "$default"
    return
  fi
  local line
  line="$(grep -E "^[[:space:]]*${key}=" "$env_file" | tail -n1 || true)"
  if [[ -z "$line" ]]; then
    echo "$default"
    return
  fi
  echo "$line" | sed -E "s/^[[:space:]]*${key}=//" | sed -E 's/^["'\'']|["'\'']$//g'
}

DB="$(read_env POSTGRES_DB noto_zhihui)"
USER="$(read_env POSTGRES_USER postgres)"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_FILE="$OUT_DIR/noto-$STAMP.sql.gz"

if ! docker ps --format '{{.Names}}' | grep -qx 'noto-db'; then
  echo "noto-db container not running. Start: ./deploy/prod-up.sh"
  exit 1
fi

echo "== Backup $DB -> $OUT_FILE =="
docker exec noto-db pg_dump -U "$USER" "$DB" | gzip > "$OUT_FILE"
echo "Backup saved: $OUT_FILE"

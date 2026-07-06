#!/usr/bin/env bash
# 生产环境 .env 上线前检查
# 用法：./deploy/prod-check.sh
#       ./deploy/prod-check.sh --strict

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/.env"
EXAMPLE="$ROOT/deploy/env.prod.example"
STRICT=0

if [[ "${1:-}" == "--strict" ]]; then
  STRICT=1
fi

read_env() {
  local key="$1"
  local default="${2:-}"
  if [[ ! -f "$ENV_FILE" ]]; then
    echo "$default"
    return
  fi
  local line
  line="$(grep -E "^[[:space:]]*${key}=" "$ENV_FILE" | tail -n1 || true)"
  if [[ -z "$line" ]]; then
    echo "$default"
    return
  fi
  echo "$line" | sed -E "s/^[[:space:]]*${key}=//" | sed -E 's/^["'\'']|["'\'']$//g'
}

is_placeholder() {
  local value="${1,,}"
  [[ -z "$value" ]] && return 0
  [[ "$value" =~ 请改|your_|changeme|example|placeholder|admin123 ]] && return 0
  [[ "$value" =~ noto-zhihui-docker-secret|noto-zhihui-default-secret ]] && return 0
  return 1
}

echo "== Noto production preflight =="

if [[ ! -f "$ENV_FILE" ]]; then
  if [[ -f "$EXAMPLE" ]]; then
    cp "$EXAMPLE" "$ENV_FILE"
    echo "Created .env from deploy/env.prod.example — edit secrets before deploy!"
  else
    echo "FAIL: .env missing"
    exit 1
  fi
fi

FAIL=0
JWT="$(read_env NOTO_JWT_SECRET)"
PG_PASS="$(read_env POSTGRES_PASSWORD)"
MINIO_PASS="$(read_env MINIO_ROOT_PASSWORD)"
DEMO="$(read_env NOTO_DEMO_ENABLED false)"
DEMO_USERNAME="$(read_env NOTO_DEMO_USERNAME demo)"
DEMO_CREATE_USER="$(read_env NOTO_DEMO_CREATE_USER false)"
AI="$(read_env NOTO_AI_ENABLED false)"
AI_KEY="$(read_env AI_DASHSCOPE_API_KEY)"

if [[ ${#JWT} -lt 32 ]] || is_placeholder "$JWT"; then
  echo "FAIL: NOTO_JWT_SECRET must be >= 32 random characters"
  FAIL=1
fi
if is_placeholder "$PG_PASS" || [[ "$PG_PASS" == "postgres" || "$PG_PASS" == "123456" ]]; then
  echo "FAIL: POSTGRES_PASSWORD must be a strong password"
  FAIL=1
fi
if is_placeholder "$MINIO_PASS" || [[ "$MINIO_PASS" == "minioadmin" ]]; then
  echo "FAIL: MINIO_ROOT_PASSWORD must be changed"
  FAIL=1
fi
if [[ "$DEMO" == "true" ]]; then
  if [[ "$DEMO_USERNAME" == "admin" ]]; then
    echo "FAIL: NOTO_DEMO_ENABLED=true must not use NOTO_DEMO_USERNAME=admin — use a separate demo account"
    FAIL=1
  fi
  if [[ "$DEMO_USERNAME" != "admin" && "$DEMO_CREATE_USER" != "true" ]]; then
    echo "WARN: NOTO_DEMO_CREATE_USER is not true — ensure user '$DEMO_USERNAME' already exists"
  fi
fi
if [[ "$AI" == "true" && -z "$AI_KEY" ]]; then
  echo "WARN: NOTO_AI_ENABLED=true but AI_DASHSCOPE_API_KEY is empty"
fi

if [[ $FAIL -eq 1 ]]; then
  echo "Preflight failed — fix .env (template: deploy/env.prod.example)"
  exit 1
fi

echo "Preflight passed."
exit 0

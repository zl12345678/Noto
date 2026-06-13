#!/usr/bin/env bash
# 拉取代码并重建生产栈
# 用法：./deploy/prod-update.sh

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "== Noto production update =="

if [[ -d .git ]]; then
  git pull
else
  echo "Not a git repo — skip pull."
fi

bash deploy/prod-check.sh --strict
bash deploy/prod-up.sh --skip-check

echo "Update complete. Check: docker compose logs -f backend"

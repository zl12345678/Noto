#!/usr/bin/env bash
# Configure Docker registry mirrors for fnOS / Linux NAS hosts.
#
# Usage:
#   bash deploy/fnos-docker-mirror.sh
#   bash deploy/fnos-docker-mirror.sh --alternate
#   bash deploy/fnos-docker-mirror.sh --no-test

set -euo pipefail

MODE="primary"
RUN_TEST=1

for arg in "$@"; do
  case "$arg" in
    --alternate) MODE="alternate" ;;
    --no-test) RUN_TEST=0 ;;
    *)
      echo "Unknown argument: $arg"
      echo "Usage: bash deploy/fnos-docker-mirror.sh [--alternate] [--no-test]"
      exit 2
      ;;
  esac
done

if ! command -v docker >/dev/null 2>&1; then
  echo "FAIL: docker command not found. Install/enable Docker in fnOS first."
  exit 1
fi

if ! command -v sudo >/dev/null 2>&1; then
  echo "FAIL: sudo command not found. Run as root or install sudo."
  exit 1
fi

sudo mkdir -p /etc/docker

if [[ -f /etc/docker/daemon.json ]]; then
  backup="/etc/docker/daemon.json.bak.$(date +%Y%m%d-%H%M%S)"
  sudo cp /etc/docker/daemon.json "$backup"
  echo "Backup saved: $backup"
fi

if [[ "$MODE" == "alternate" ]]; then
  cat <<'EOF' | sudo tee /etc/docker/daemon.json >/dev/null
{
  "registry-mirrors": [
    "https://docker.xuanyuan.me",
    "https://docker.1panel.live",
    "https://docker.1ms.run",
    "https://docker.m.daocloud.io"
  ]
}
EOF
else
  cat <<'EOF' | sudo tee /etc/docker/daemon.json >/dev/null
{
  "registry-mirrors": [
    "https://docker.1panel.live",
    "https://docker.1ms.run",
    "https://docker.m.daocloud.io"
  ]
}
EOF
fi

echo "Restarting Docker..."
sudo systemctl daemon-reload || true
sudo systemctl restart docker

echo
echo "Registry mirrors:"
docker info 2>/dev/null | sed -n '/Registry Mirrors:/,/Live Restore Enabled:/p' || true

if [[ $RUN_TEST -eq 1 ]]; then
  echo
  echo "Testing image pull: hello-world"
  sudo docker pull hello-world
fi

echo
echo "Docker mirror configuration complete."

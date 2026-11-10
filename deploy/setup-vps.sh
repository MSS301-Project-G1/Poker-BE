#!/usr/bin/env bash
# One-time preparation of an Ubuntu VPS for Poker: Docker Engine + Compose plugin, log rotation
# and a swap file. Safe to re-run.   Usage on the VPS:  bash setup-vps.sh
set -euo pipefail

SWAP_SIZE="${SWAP_SIZE:-4G}"
SUDO=""
if [ "$(id -u)" -ne 0 ]; then SUDO="sudo"; fi
export DEBIAN_FRONTEND=noninteractive NEEDRESTART_MODE=a

# 1) Docker Engine from Docker's official repository
if ! command -v docker >/dev/null 2>&1; then
  echo "==> Installing Docker"
  curl -fsSL https://get.docker.com | $SUDO -E sh
fi
$SUDO usermod -aG docker "$(id -un)"

# 2) Cap container logs; twelve chatty services would otherwise fill the disk over time
if [ ! -f /etc/docker/daemon.json ]; then
  echo '{"log-driver":"json-file","log-opts":{"max-size":"10m","max-file":"3"}}' \
    | $SUDO tee /etc/docker/daemon.json >/dev/null
  $SUDO systemctl restart docker
fi
$SUDO systemctl enable --now docker >/dev/null 2>&1

# 3) Swap as a safety net for the JVMs' memory peaks
if ! swapon --show | grep -q .; then
  echo "==> Creating $SWAP_SIZE swap"
  $SUDO fallocate -l "$SWAP_SIZE" /swapfile
  $SUDO chmod 600 /swapfile
  $SUDO mkswap /swapfile >/dev/null
  $SUDO swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' | $SUDO tee -a /etc/fstab >/dev/null
fi

$SUDO docker --version
$SUDO docker compose version
free -h | grep -i swap
echo "==> VPS ready ✅  (log out and back in once so '$(id -un)' can run docker without sudo)"

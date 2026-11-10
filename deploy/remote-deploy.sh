#!/usr/bin/env bash
# Runs on the VPS; GitHub Actions calls it after pushing the images to GHCR:
#   printf '%s' "$TOKEN" | GHCR_USER=<user> bash deploy/remote-deploy.sh <image-tag>
# The registry token arrives on stdin so it never appears in the process list or in .env.
set -euo pipefail

TAG="${1:?usage: remote-deploy.sh <image-tag>}"
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$APP_DIR"

COMPOSE=(docker compose -f docker-compose.yml -f deploy/compose.prod.yml)
GATEWAY_HEALTH_URL="http://127.0.0.1:8080/actuator/health"
HEALTH_ATTEMPTS=72   # x5s = 6 minutes: twelve JVMs starting together on a 2-core VPS are slow
FE_PROJECT="poker-fe"

if ! command -v docker >/dev/null 2>&1; then
  echo "::error::Docker is not installed on the VPS. Run deploy/setup-vps.sh once."
  exit 1
fi
if [ ! -f .env ]; then
  echo "::error::$APP_DIR/.env is missing. Create it from deploy/prod.env.example."
  exit 1
fi

docker login ghcr.io -u "${GHCR_USER:?GHCR_USER is not set}" --password-stdin >/dev/null
trap 'docker logout ghcr.io >/dev/null 2>&1 || true' EXIT

echo "==> Pulling images ($TAG)"
IMAGE_TAG="$TAG" "${COMPOSE[@]}" pull --quiet

# Record the tag so manual `docker compose` commands on the VPS use the same images.
if grep -q '^IMAGE_TAG=' .env; then
  sed -i "s|^IMAGE_TAG=.*|IMAGE_TAG=$TAG|" .env
else
  echo "IMAGE_TAG=$TAG" >> .env
fi

echo "==> Starting services"
"${COMPOSE[@]}" up -d --no-build --remove-orphans

echo "==> Waiting for the gateway"
healthy=false
for _ in $(seq 1 "$HEALTH_ATTEMPTS"); do
  if curl -fsS --max-time 3 "$GATEWAY_HEALTH_URL" >/dev/null 2>&1; then
    healthy=true
    break
  fi
  sleep 5
done

"${COMPOSE[@]}" ps --format 'table {{.Service}}\t{{.Status}}'

if [ "$healthy" != true ]; then
  echo "::error::The gateway is not healthy after $((HEALTH_ATTEMPTS * 5))s"
  "${COMPOSE[@]}" logs --tail 40 api-gateway auth-service discovery-service || true
  exit 1
fi

# nginx in the FE container resolves service names only when it loads its config, and recreated
# backend containers may get new IPs. A reload picks them up; if a name does not resolve, nginx
# keeps serving with the old config instead of going down like a restart would.
for container in $(docker ps -q --filter "label=com.docker.compose.project=$FE_PROJECT"); do
  echo "==> Reloading nginx in the FE container"
  docker exec "$container" nginx -s reload || true
done

docker image prune -af --filter "until=168h" >/dev/null
echo "==> Backend $TAG is up ✅"

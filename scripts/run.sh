#!/usr/bin/env bash
set -euo pipefail

IMAGE_NAME="${IMAGE_NAME:-careerpolitics/scraper}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
CONTAINER_NAME="${CONTAINER_NAME:-careerpolitics-scraper-local}"
APP_PORT="${APP_PORT:-8080}"
ENV_FILE="${ENV_FILE:-.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: env file not found at $ENV_FILE"
  exit 1
fi

docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true

docker run -d \
  --name "$CONTAINER_NAME" \
  --restart unless-stopped \
  --env-file "$ENV_FILE" \
  -p "${APP_PORT}:8080" \
  "${IMAGE_NAME}:${IMAGE_TAG}"

echo "Container started: $CONTAINER_NAME"
docker logs --tail=50 "$CONTAINER_NAME"

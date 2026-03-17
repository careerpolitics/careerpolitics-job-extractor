#!/usr/bin/env bash
set -euo pipefail

IMAGE_NAME="${IMAGE_NAME:-careerpolitics/scraper}"
VERSION="${VERSION:-latest}"
CONTAINER_NAME="${CONTAINER_NAME:-careerpolitics-scraper}"
APP_PORT="${APP_PORT:-8080}"
ENV_FILE="${ENV_FILE:-.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: env file not found at $ENV_FILE"
  exit 1
fi

echo "Pulling ${IMAGE_NAME}:${VERSION}"
docker pull "${IMAGE_NAME}:${VERSION}"

echo "Stopping old container if present"
docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true

echo "Starting container ${CONTAINER_NAME}"
docker run -d \
  --name "$CONTAINER_NAME" \
  --restart unless-stopped \
  --env-file "$ENV_FILE" \
  -p "${APP_PORT}:8080" \
  "${IMAGE_NAME}:${VERSION}"

echo "Deployment complete"
docker ps --filter "name=${CONTAINER_NAME}"

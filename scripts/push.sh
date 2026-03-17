#!/usr/bin/env bash
set -euo pipefail

IMAGE_NAME="${IMAGE_NAME:-careerpolitics/scraper}"
VERSION="${VERSION:-}"

if [[ -z "$VERSION" ]]; then
  echo "ERROR: VERSION is required. Example: VERSION=2026.03.17 ./scripts/push.sh"
  exit 1
fi

echo "Pushing ${IMAGE_NAME}:${VERSION}"
docker push "${IMAGE_NAME}:${VERSION}"

echo "Pushing ${IMAGE_NAME}:latest"
docker push "${IMAGE_NAME}:latest"

echo "Push complete"

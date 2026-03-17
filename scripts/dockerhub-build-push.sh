#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   IMAGE_NAME=<dockerhub_username> IMAGE_REPO=careerpolitics-job-extractor IMAGE_TAG=v1 ./scripts/dockerhub-build-push.sh

IMAGE_NAME="${IMAGE_NAME:?set IMAGE_NAME (Docker Hub username/org)}"
IMAGE_REPO="${IMAGE_REPO:-careerpolitics-job-extractor}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
FULL_IMAGE="${IMAGE_NAME}/${IMAGE_REPO}:${IMAGE_TAG}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required" >&2
  exit 1
fi

echo "==> Building image: ${FULL_IMAGE}"
docker build -t "${FULL_IMAGE}" -f Dockerfile .

echo "==> Pushing image: ${FULL_IMAGE}"
docker push "${FULL_IMAGE}"

echo "Done. Pushed ${FULL_IMAGE}"

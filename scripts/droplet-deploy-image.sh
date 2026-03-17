#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   DROPLET_HOST=1.2.3.4 DROPLET_USER=root IMAGE_NAME=<dockerhub_username> IMAGE_REPO=careerpolitics-job-extractor IMAGE_TAG=v1 ./scripts/droplet-deploy-image.sh
#
# Requirements:
# - .env.droplet exists locally with your runtime env variables
# - SSH access to droplet is already configured

DROPLET_HOST="${DROPLET_HOST:?set DROPLET_HOST}"
DROPLET_USER="${DROPLET_USER:-root}"
DROPLET_PATH="${DROPLET_PATH:-/opt/careerpolitics-job-extractor}"
IMAGE_NAME="${IMAGE_NAME:?set IMAGE_NAME}"
IMAGE_REPO="${IMAGE_REPO:-careerpolitics-job-extractor}"
IMAGE_TAG="${IMAGE_TAG:-latest}"

if [[ ! -f .env.droplet ]]; then
  echo ".env.droplet not found. Create it first (cp .env.droplet.example .env.droplet)." >&2
  exit 1
fi

echo "==> Uploading compose + env to ${DROPLET_USER}@${DROPLET_HOST}:${DROPLET_PATH}"
ssh "${DROPLET_USER}@${DROPLET_HOST}" "mkdir -p '${DROPLET_PATH}'"
scp docker-compose.droplet.yml .env.droplet "${DROPLET_USER}@${DROPLET_HOST}:${DROPLET_PATH}/"

echo "==> Pulling image and restarting service on droplet"
ssh "${DROPLET_USER}@${DROPLET_HOST}" "cd '${DROPLET_PATH}' && \
  IMAGE_NAME='${IMAGE_NAME}' IMAGE_REPO='${IMAGE_REPO}' IMAGE_TAG='${IMAGE_TAG}' docker compose -f docker-compose.droplet.yml pull && \
  IMAGE_NAME='${IMAGE_NAME}' IMAGE_REPO='${IMAGE_REPO}' IMAGE_TAG='${IMAGE_TAG}' docker compose -f docker-compose.droplet.yml up -d"

echo "Deployment completed."

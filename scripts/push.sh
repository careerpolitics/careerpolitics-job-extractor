#!/usr/bin/env bash
set -euo pipefail

IMAGE_NAME="${IMAGE_NAME:-careerpolitics/scraper}"
IMAGE_TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD)}"

echo "Pushing image with Jib to registry: ${IMAGE_NAME}:${IMAGE_TAG}"
IMAGE_NAME="$IMAGE_NAME" IMAGE_TAG="$IMAGE_TAG" ./gradlew jib

echo "Pushed tags: ${IMAGE_NAME}:${IMAGE_TAG}, ${IMAGE_NAME}:latest"

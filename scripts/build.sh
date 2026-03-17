#!/usr/bin/env bash
set -euo pipefail

IMAGE_NAME="${IMAGE_NAME:-careerpolitics/scraper}"
IMAGE_TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD)}"

echo "Building local Docker image with Jib: ${IMAGE_NAME}:${IMAGE_TAG}"
IMAGE_NAME="$IMAGE_NAME" IMAGE_TAG="$IMAGE_TAG" ./gradlew jibDockerBuild

echo "Built tags: ${IMAGE_NAME}:${IMAGE_TAG}, ${IMAGE_NAME}:latest"

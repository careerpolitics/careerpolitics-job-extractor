#!/usr/bin/env bash
set -euo pipefail

IMAGE_NAME="${IMAGE_NAME:-careerpolitics/scraper}"
VERSION="${VERSION:-$(date +%Y.%m.%d)-$(git rev-parse --short HEAD)}"

printf 'Building image %s with tags [%s, latest]\n' "$IMAGE_NAME" "$VERSION"
docker build -t "${IMAGE_NAME}:${VERSION}" -t "${IMAGE_NAME}:latest" .

printf 'Build complete.\n'
printf '  %s\n' "${IMAGE_NAME}:${VERSION}"
printf '  %s\n' "${IMAGE_NAME}:latest"

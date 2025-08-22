#!/usr/bin/env bash
set -euo pipefail
BASE_URL=${BASE_URL:-http://localhost:8080/api}
OUT_DIR=${OUT_DIR:-.}
mkdir -p "$OUT_DIR"

curl -fsSL "$BASE_URL/v3/api-docs" -o "$OUT_DIR/openapi.json"
echo "Wrote $OUT_DIR/openapi.json"

curl -fsSL "$BASE_URL/v3/api-docs.yaml" -o "$OUT_DIR/openapi.yaml" || true
if [ -f "$OUT_DIR/openapi.yaml" ]; then
  echo "Wrote $OUT_DIR/openapi.yaml"
else
  echo "YAML endpoint not available; only JSON exported"
fi
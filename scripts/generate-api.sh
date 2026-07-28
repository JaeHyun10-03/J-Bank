#!/usr/bin/env bash
# 실행 중인 백엔드에서 OpenAPI 명세를 덤프하고 프론트 타입을 재생성한다.
# 백엔드가 localhost:8080에 떠 있어야 한다.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

curl -sf http://localhost:8080/v3/api-docs.yaml -o "$ROOT_DIR/contracts/openapi/openapi.yaml"
(cd "$ROOT_DIR/apps/frontend" && npx openapi-typescript "$ROOT_DIR/contracts/openapi/openapi.yaml" -o types/api.ts)

echo "contracts/openapi/openapi.yaml, apps/frontend/types/api.ts 갱신됨"

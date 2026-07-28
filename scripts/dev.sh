#!/usr/bin/env bash
# 로컬 인프라를 지정한 프로파일로만 기동한다. 전체 기동 단축 명령은 두지 않는다(폴더구조 7절).
# 사용법: scripts/dev.sh core | scripts/dev.sh core messaging | scripts/dev.sh core observability
set -euo pipefail

if [ "$#" -eq 0 ]; then
  echo "사용법: $0 <profile...>  (예: core / core messaging / core observability)"
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARGS=()
for profile in "$@"; do
  ARGS+=(--profile "$profile")
done

docker compose -f "$ROOT_DIR/infra/compose/docker-compose.yml" "${ARGS[@]}" up -d

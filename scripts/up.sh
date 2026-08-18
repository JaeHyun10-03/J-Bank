#!/usr/bin/env bash
# 로컬 풀스택 한 번에 기동: Docker(core+messaging+observability) + 백엔드 + 프론트엔드.
# 성능 측정(k6) 시에는 이 스크립트 대신 `scripts/dev.sh core`로 프로파일을 좁혀서 조건을 고정할 것.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT_DIR/.local-logs"
mkdir -p "$LOG_DIR"

echo "== 로컬 인프라(core+messaging+observability) 기동 =="
"$ROOT_DIR/scripts/dev.sh" core messaging observability

echo "== 백엔드 기동 (로그: $LOG_DIR/backend.log) =="
(cd "$ROOT_DIR/apps/jbank-api" && ./gradlew bootRun --args='--spring.profiles.active=local') \
  >"$LOG_DIR/backend.log" 2>&1 &
BACKEND_PID=$!

echo "== 프론트엔드 기동 (로그: $LOG_DIR/frontend.log) =="
(cd "$ROOT_DIR/apps/frontend" && npm run dev) \
  >"$LOG_DIR/frontend.log" 2>&1 &
FRONTEND_PID=$!

cleanup() {
  echo
  echo "== 백엔드·프론트엔드 종료 (Docker는 유지, 내리려면 scripts/clean.sh) =="
  kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
  wait "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
}
trap cleanup INT TERM

echo "== 모두 기동됨. 로그: tail -f $LOG_DIR/backend.log $LOG_DIR/frontend.log =="
echo "== Ctrl+C로 백엔드/프론트엔드 종료 =="
wait "$BACKEND_PID" "$FRONTEND_PID"

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

wait_for() {
  local url="$1" tries=60
  until curl -s -o /dev/null "$url"; do
    tries=$((tries - 1))
    [ "$tries" -le 0 ] && return 1
    sleep 2
  done
}

echo "== 백엔드/프론트엔드 준비 대기 중 =="
wait_for "http://localhost:8080/actuator/health" || echo "  (백엔드 응답 지연 — $LOG_DIR/backend.log 확인)"
wait_for "http://localhost:3000" || echo "  (프론트엔드 응답 지연 — $LOG_DIR/frontend.log 확인)"

cat <<LINKS

== 준비됨 ==
프론트엔드     http://localhost:3000
백엔드         http://localhost:8080
스웨거 UI      http://localhost:8080/swagger-ui.html
로그           tail -f $LOG_DIR/backend.log $LOG_DIR/frontend.log

Ctrl+C로 백엔드/프론트엔드 종료 (Docker는 유지, 내리려면 scripts/clean.sh)
LINKS

wait "$BACKEND_PID" "$FRONTEND_PID"

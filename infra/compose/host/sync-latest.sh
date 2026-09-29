#!/usr/bin/env bash
# 꺼져 있는 동안 main에 올라온 이미지를 반영한다. boot.sh가 배포 잠금을 쥔 채로 부른다 —
# 잠금은 여기서 잡지 않는다(backend-cd와 같은 잠금 파일, 중첩하면 교착).
# :latest의 revision 라벨(backend-cd가 붙임)이 .env의 IMAGE_TAG와 다를 때만 교체한다.
# 어느 단계가 실패해도 현재 이미지를 유지하고 0으로 끝난다 — 배치는 계속 돌아야 한다.
set -uo pipefail

REPO="${JBANK_REPO:-/opt/jbank}"
COMPOSE_DIR="$REPO/infra/compose"
IMAGE="ghcr.io/jaehyun10-03/jbank-api"

log() { echo "$(date -u +%FT%TZ) sync-latest: $*"; }

if ! git -C "$REPO" fetch -q origin main || ! git -C "$REPO" reset -q --hard origin/main; then
  log "저장소 갱신 실패 — 현재 저장소로 계속"
fi

if ! docker pull -q "$IMAGE:latest" >/dev/null; then
  log ":latest pull 실패 — 현재 이미지 유지"
  exit 0
fi
latest=$(docker image inspect -f '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$IMAGE:latest" 2>/dev/null || true)
current=$(sed -n 's/^IMAGE_TAG=//p' "$COMPOSE_DIR/.env")

if [ -z "$latest" ] || [ "$latest" = "<no value>" ]; then
  log ":latest에 revision 라벨 없음 — 현재 이미지($current) 유지"
  exit 0
fi
if [ "$latest" = "$current" ]; then
  log "최신과 같음($current) — 교체 안 함"
  exit 0
fi

log "교체: $current → $latest"
if IMAGE_TAG="$latest" bash "$COMPOSE_DIR/deploy.sh"; then
  log "교체 완료: $latest"
else
  log "교체 실패 — 확인 필요(현재 .env: $(sed -n 's/^IMAGE_TAG=//p' "$COMPOSE_DIR/.env"))"
fi
exit 0

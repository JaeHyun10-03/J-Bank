#!/usr/bin/env bash
# EC2에서 실행. backend-cd.yml이 SSM으로 호출하지만 손으로 돌려도 같다.
# 사용법: IMAGE_TAG=<git sha> infra/compose/deploy.sh   (비우면 .env의 값 유지)
# api만 새 이미지로 재기동한다 — postgres·redis·prometheus·grafana·caddy는 건드리지 않는다.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

if [ -n "${IMAGE_TAG:-}" ]; then
  sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=${IMAGE_TAG}/" .env
fi

docker compose -f docker-compose.prod.yml pull -q api
docker compose -f docker-compose.prod.yml up -d api
docker image prune -f >/dev/null

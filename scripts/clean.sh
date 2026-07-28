#!/usr/bin/env bash
# 로컬 Compose 스택을 전부 내리고 볼륨까지 지운다.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
docker compose -f "$ROOT_DIR/infra/compose/docker-compose.yml" --profile core --profile messaging --profile observability down -v

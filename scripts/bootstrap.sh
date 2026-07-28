#!/usr/bin/env bash
# 최초 1회 세팅: 도구 버전 확인, 의존성 설치, core 프로파일 기동까지.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "== 도구 버전 확인 =="
java -version
docker --version
node --version

echo "== 백엔드 의존성 확인 =="
(cd "$ROOT_DIR/apps/jbank-api" && ./gradlew --version)

echo "== 프론트엔드 의존성 설치 =="
(cd "$ROOT_DIR/apps/frontend" && npm install)

echo "== 로컬 인프라(core) 기동 =="
"$ROOT_DIR/scripts/dev.sh" core

echo "완료. 백엔드는 apps/jbank-api에서 ./gradlew bootRun --args='--spring.profiles.active=local'"

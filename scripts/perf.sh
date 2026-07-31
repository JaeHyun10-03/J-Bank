#!/usr/bin/env bash
# k6 이체 부하 테스트를 실행하고 결과를 perf/results에 저장한다. 실행 조건 기록 규약은
# perf/README.md를 따른다. 백엔드가 로컬에 떠 있고 FROM/TO 계좌에 잔액이 충분해야 한다.
# 사용법: scripts/perf.sh <라벨> <출금계좌번호> <입금계좌번호>
#   예:   scripts/perf.sh w2-baseline 110-000001-5 110-000002-3
set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "사용법: $0 <라벨> <출금계좌번호> <입금계좌번호>"
  exit 1
fi

LABEL="$1"
FROM_ACCOUNT_NUMBER="$2"
TO_ACCOUNT_NUMBER="$3"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATE="$(date +%Y-%m-%d)"
OUT_DIR="$ROOT_DIR/perf/results"
mkdir -p "$OUT_DIR"

FROM_ACCOUNT_NUMBER="$FROM_ACCOUNT_NUMBER" \
TO_ACCOUNT_NUMBER="$TO_ACCOUNT_NUMBER" \
  k6 run \
    --summary-export="$OUT_DIR/${DATE}-${LABEL}.json" \
    "$ROOT_DIR/perf/k6/transfer.js" \
  | tee "$OUT_DIR/${DATE}-${LABEL}.log"

echo "결과 저장됨: $OUT_DIR/${DATE}-${LABEL}.json, ${DATE}-${LABEL}.log"

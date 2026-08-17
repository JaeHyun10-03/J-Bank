#!/usr/bin/env bash
# k6 이체 부하 테스트를 실행하고 결과를 perf/results에 저장한다. 실행 조건 기록 규약은
# perf/README.md를 따른다. 백엔드가 로컬에 떠 있고, LOGIN_ID 계정이 FROM/TO 계좌를 둘 다
# 소유하며(방향을 번갈아 보내므로 양쪽 다 발신 계좌가 됨) 잔액이 충분해야 한다.
# 사용법: scripts/perf.sh <라벨> <로그인ID> <비밀번호> <출금계좌번호> <입금계좌번호>
#   예:   scripts/perf.sh w2-baseline perf-tester perf-pass-1234 110-000001-5 110-000002-3
set -euo pipefail

if [ "$#" -ne 5 ]; then
  echo "사용법: $0 <라벨> <로그인ID> <비밀번호> <출금계좌번호> <입금계좌번호>"
  exit 1
fi

LABEL="$1"
LOGIN_ID="$2"
PASSWORD="$3"
FROM_ACCOUNT_NUMBER="$4"
TO_ACCOUNT_NUMBER="$5"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATE="$(date +%Y-%m-%d)"
OUT_DIR="$ROOT_DIR/perf/results"
mkdir -p "$OUT_DIR"

LOGIN_ID="$LOGIN_ID" \
PASSWORD="$PASSWORD" \
FROM_ACCOUNT_NUMBER="$FROM_ACCOUNT_NUMBER" \
TO_ACCOUNT_NUMBER="$TO_ACCOUNT_NUMBER" \
  k6 run \
    --summary-export="$OUT_DIR/${DATE}-${LABEL}.json" \
    "$ROOT_DIR/perf/k6/transfer.js" \
  | tee "$OUT_DIR/${DATE}-${LABEL}.log"

# k6 summary-export가 setup()의 반환값(로그인 JWT·CSRF 토큰)을 그대로 setup_data에
# 직렬화한다. 커밋되는 파일이라 토큰이 새어나가지 않도록 지운다.
jq '.setup_data = {}' "$OUT_DIR/${DATE}-${LABEL}.json" > "$OUT_DIR/${DATE}-${LABEL}.json.tmp"
mv "$OUT_DIR/${DATE}-${LABEL}.json.tmp" "$OUT_DIR/${DATE}-${LABEL}.json"

echo "결과 저장됨: $OUT_DIR/${DATE}-${LABEL}.json, ${DATE}-${LABEL}.log"

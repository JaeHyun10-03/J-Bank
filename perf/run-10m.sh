#!/usr/bin/env bash
# 대규모(계좌 10만·거래 1천만) 성능 측정 1회 실행. perf/README.md의 "대규모 시드" 절 참고.
#
# 선행 조건:
#   1. scripts/dev.sh core 로 PostgreSQL·Redis 기동
#   2. apps/jbank-api 백엔드가 localhost:8080 에 떠 있음 (Flyway 적용 완료)
#   3. k6 설치, docker compose 사용 가능
#   4. transactions 테이블이 비어 있음(1000건 미만) — 재실행하려면 아래 정리 절차부터.
#
# 정리(재실행 전):
#   docker compose -f infra/compose/docker-compose.yml exec -T postgres psql -U jbank -d jbank -c \
#     "TRUNCATE transactions, ledger_entries RESTART IDENTITY; \
#      DELETE FROM accounts WHERE account_number LIKE '900%'; \
#      DELETE FROM customers WHERE login_id LIKE 'seed-user-%';"
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
COMPOSE="docker compose -f $ROOT_DIR/infra/compose/docker-compose.yml"
PSQL="$COMPOSE exec -T postgres psql -U jbank -d jbank -v ON_ERROR_STOP=1"
DATE="$(date +%Y-%m-%d)"
OUT_DIR="$ROOT_DIR/perf/results"
mkdir -p "$OUT_DIR"

LOGIN_ID="perf10m"
PASSWORD="perf-pass-1234"
RRN="8811112345678"

log() { printf '\n=== %s ===\n' "$1"; }

# --- 0. 백엔드 확인 ---------------------------------------------------------
log "백엔드 헬스체크"
curl -fsS "$BASE_URL/actuator/health" >/dev/null || {
  echo "백엔드가 $BASE_URL 에 없다. apps/jbank-api를 먼저 기동할 것."; exit 1;
}

# --- 1. 이체 테스트용 계좌 준비 (거래 시드 이전에 만들어 풀에 포함시킨다) ---
log "이체 테스트 계좌 준비"
# 고객 등록(공개 엔드포인트, CSRF 예외). 이미 있으면 무시하고 진행.
curl -fsS -X POST "$BASE_URL/api/v1/customers" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"perf tester\",\"loginId\":\"$LOGIN_ID\",\"password\":\"$PASSWORD\",
       \"residentRegNo\":\"$RRN\",\"birthDate\":\"1988-11-11\",\"phone\":\"010-9999-0001\",
       \"address\":\"서울\",\"occupation\":\"회사원\",
       \"identityVerificationMethod\":\"FACE_TO_FACE\",
       \"transactionPurpose\":\"급여\",\"fundSource\":\"근로소득\"}' >/dev/null 2>&1 \
  || echo "  (고객이 이미 있는 듯 — 로그인으로 진행)"

# 로그인: access_token / XSRF-TOKEN 쿠키값을 Set-Cookie에서 직접 뽑는다
# (Secure 속성이라 curl 쿠키 저장소가 http://로 안 돌려보냄). 응답 본문에서 customerId도 얻는다.
TMPH="$(mktemp)"; TMPB="$(mktemp)"; trap 'rm -f "$TMPH" "$TMPB"' EXIT
curl -fsS -D "$TMPH" -o "$TMPB" -X POST "$BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"loginId\":\"$LOGIN_ID\",\"password\":\"$PASSWORD\"}"
ACCESS="$(sed -n 's/.*access_token=\([^;]*\).*/\1/p' "$TMPH" | head -1)"
XSRF="$(sed -n 's/.*XSRF-TOKEN=\([^;]*\).*/\1/p' "$TMPH" | head -1)"
CUSTOMER_ID="$(sed -n 's/.*"customerId":"\{0,1\}\([0-9]*\).*/\1/p' "$TMPB" | head -1)"
[ -n "$ACCESS" ] && [ -n "$XSRF" ] || { echo "로그인 토큰 추출 실패"; exit 1; }
AUTH=(-H "Cookie: access_token=$ACCESS; XSRF-TOKEN=$XSRF" -H "X-CSRF-TOKEN: $XSRF")

open_account() {
  curl -fsS -X POST "$BASE_URL/api/v1/accounts" -H 'Content-Type: application/json' "${AUTH[@]}" \
    -d '{"productType":"CHECKING","initialDeposit":0}'
}
deposit() { # $1 accountId
  curl -fsS -X POST "$BASE_URL/api/v1/accounts/$1/deposit" -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $(uuidgen)" "${AUTH[@]}" \
    -d '{"amount":"100000000.00","channel":"BRANCH"}' >/dev/null
}

A1_JSON="$(open_account)"; A2_JSON="$(open_account)"
A1_ID="$(printf '%s' "$A1_JSON" | sed -n 's/.*"accountId":"\{0,1\}\([0-9]*\).*/\1/p' | head -1)"
A2_ID="$(printf '%s' "$A2_JSON" | sed -n 's/.*"accountId":"\{0,1\}\([0-9]*\).*/\1/p' | head -1)"
A1_NO="$(printf '%s' "$A1_JSON" | sed -n 's/.*"accountNumber":"\([^"]*\).*/\1/p' | head -1)"
A2_NO="$(printf '%s' "$A2_JSON" | sed -n 's/.*"accountNumber":"\([^"]*\).*/\1/p' | head -1)"
deposit "$A1_ID"; deposit "$A2_ID"
echo "  from=$A1_NO ($A1_ID)  to=$A2_NO ($A2_ID)  customer=$CUSTOMER_ID"

# --- 2. 대규모 시드 -------------------------------------------------------
log "seed-10m.sql 적재 (수 분 소요)"
time ($PSQL < "$ROOT_DIR/perf/sql/seed-10m.sql")

$PSQL -c "SELECT
  (SELECT count(*) FROM customers)    AS customers,
  (SELECT count(*) FROM accounts)     AS accounts,
  (SELECT count(*) FROM transactions) AS transactions;"

# --- 3. 거래내역 조회 EXPLAIN (저매칭 계좌 = 이체 테스트 계좌) -----------
log "거래내역 조회 EXPLAIN — account_id=$A1_ID"
MATCH="$($PSQL -tA -c "SELECT count(*) FROM transactions WHERE from_account_id=$A1_ID OR to_account_id=$A1_ID;")"
echo "  매칭 행: $MATCH / 10,000,000"
{
  echo "-- SELECT (LIMIT 20, 정렬 없음: JPA findAll 기본형)"
  $PSQL -c "EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM transactions
    WHERE from_account_id=$A1_ID OR to_account_id=$A1_ID LIMIT 20;"
  echo "-- SELECT (ORDER BY transaction_id DESC LIMIT 20)"
  $PSQL -c "EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM transactions
    WHERE from_account_id=$A1_ID OR to_account_id=$A1_ID ORDER BY transaction_id DESC LIMIT 20;"
  echo "-- COUNT(*) (Spring Data Page가 총건수 계산에 실행하는 쿼리)"
  $PSQL -c "EXPLAIN (ANALYZE, BUFFERS) SELECT count(*) FROM transactions
    WHERE from_account_id=$A1_ID OR to_account_id=$A1_ID;"
} | tee "$OUT_DIR/${DATE}-10m-explain.log"

# --- 4. k6 조회계 부하 ---------------------------------------------------
for EP in history balance account customer-accounts; do
  log "k6 read — $EP"
  LOGIN_ID="$LOGIN_ID" PASSWORD="$PASSWORD" ACCOUNT_ID="$A1_ID" ENDPOINT="$EP" \
    k6 run --summary-export="$OUT_DIR/${DATE}-10m-read-${EP}.json" \
      "$ROOT_DIR/perf/k6/read-endpoints.js" | tee "$OUT_DIR/${DATE}-10m-read-${EP}.log"
  jq '.setup_data = {}' "$OUT_DIR/${DATE}-10m-read-${EP}.json" > "$OUT_DIR/tmp.json"
  mv "$OUT_DIR/tmp.json" "$OUT_DIR/${DATE}-10m-read-${EP}.json"
done

# --- 5. 이체 부하 (기존 스크립트 재사용) --------------------------------
log "k6 transfer — 1천만 건 상태 재측정"
"$ROOT_DIR/scripts/perf.sh" 10m-transfer "$LOGIN_ID" "$PASSWORD" "$A1_NO" "$A2_NO"

log "완료 — 결과는 perf/results/${DATE}-10m-*"

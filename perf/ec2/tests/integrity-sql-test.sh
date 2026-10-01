#!/usr/bin/env bash
# perf/ec2/sql/credit-integrity.sql 검사. 로컬 PostgreSQL 컨테이너에 최소 스키마와 가짜 행을 넣고 기대값과 비교한다.
# 실행: bash perf/ec2/tests/integrity-sql-test.sh   (Docker 필요)
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NAME="credit-integrity-test-$$"
docker run -d --rm --name "$NAME" -e POSTGRES_PASSWORD=x postgres:16-alpine >/dev/null
trap 'docker stop "$NAME" >/dev/null' EXIT
until docker exec "$NAME" pg_isready -U postgres >/dev/null 2>&1; do sleep 1; done
sleep 1
q() { docker exec -i "$NAME" psql -U postgres -v ON_ERROR_STOP=1 "$@"; }
q -q <<'SQL'
CREATE TABLE transactions (transaction_id bigint PRIMARY KEY, transaction_type text, status text);
CREATE TABLE ledger_entries (entry_id bigserial PRIMARY KEY, transaction_id bigint, entry_type text, amount numeric);
CREATE TABLE pending_credits (transaction_id bigint, account_id bigint, amount numeric, applied_at timestamptz);
-- 경계(b_tx=10) 이전 거래는 무시되어야 한다.
INSERT INTO transactions VALUES (5, 'TRANSFER', 'COMPLETED');
INSERT INTO pending_credits VALUES (5, 1, 999, NULL);
-- 경계 원장(b_le=1) 이전 행. entry_id가 경계 이하라 대변 원장 수에 들어가면 안 된다(11이 2건으로 잡히면 필터가 틀린 것).
INSERT INTO ledger_entries (transaction_id, entry_type, amount) VALUES (11, 'CREDIT', 100);
-- 11: 반영 완료(대변 1) 12: 미반영(대변 0) 13: 반영됐는데 대변 2(중복) 14: 입금 대기 없음(유실) 15: 핫 계좌(1) 미반영
INSERT INTO transactions VALUES (11,'TRANSFER','COMPLETED'),(12,'TRANSFER','COMPLETED'),(13,'TRANSFER','COMPLETED'),
  (14,'TRANSFER','COMPLETED'),(15,'TRANSFER','COMPLETED'),(16,'DEPOSIT','COMPLETED');
INSERT INTO pending_credits VALUES (11,2,100,now()),(12,2,200,NULL),(13,2,300,now()),(15,1,1000,NULL);
INSERT INTO ledger_entries (transaction_id, entry_type, amount) VALUES
  (11,'DEBIT',100),(11,'CREDIT',100),(12,'DEBIT',200),(13,'DEBIT',300),(13,'CREDIT',300),(13,'CREDIT',300),(14,'DEBIT',50),(15,'DEBIT',1000);
SQL
out=$(q -v b_tx=10 -v b_le=1 -v hot_id=1 < "$HERE/../sql/credit-integrity.sql")
echo "$out"
expect() { grep -qx "$1" <<<"$out" || { echo "FAIL: $1 기대"; exit 1; }; }
expect "new_unapplied_count=2"
expect "new_unapplied_sum=1200"
expect "hot_unapplied_sum=1000"
expect "all_unapplied_sum=2199"
expect "transfer_pending_row_mismatch=1"    # 14
expect "transfer_credit_entry_mismatch=1"   # 13(중복). 14는 대기 행이 없어 윗줄(대기 행 수 불일치)에서 잡힌다
# 경계를 0으로 두면 경계 이전 행까지 세어 11도 불일치가 된다 — 위 결과가 경계 필터 덕분임을 확인.
out=$(q -v b_tx=10 -v b_le=0 -v hot_id=1 < "$HERE/../sql/credit-integrity.sql")
expect "transfer_credit_entry_mismatch=2"   # 11(경계 이전 행 포함), 13
echo "integrity-sql-test: ok"

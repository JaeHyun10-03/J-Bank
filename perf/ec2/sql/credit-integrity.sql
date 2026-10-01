-- 입금 비동기 반영(ADR 0012) 뒤의 정합성 추가 항목. target.sh integrity가 부르고, perf/ec2/tests/integrity-sql-test.sh가
-- 가짜 행으로 같은 파일을 검사한다. 변수: b_tx(경계 거래 ID), b_le(경계 원장 ID), hot_id(핫 계좌 ID). 출력은 key=value 한 줄씩.
\pset format unaligned
\pset fieldsep '='
\pset tuples_only on
SELECT 'new_unapplied_count', count(*) FROM pending_credits WHERE transaction_id > :b_tx AND applied_at IS NULL;
SELECT 'new_unapplied_sum', coalesce(sum(amount), 0) FROM pending_credits WHERE transaction_id > :b_tx AND applied_at IS NULL;
SELECT 'hot_unapplied_sum', coalesce(sum(amount), 0) FROM pending_credits
 WHERE transaction_id > :b_tx AND applied_at IS NULL AND account_id = :hot_id;
SELECT 'all_unapplied_sum', coalesce(sum(amount), 0) FROM pending_credits WHERE applied_at IS NULL;
-- 완료 이체마다 입금 대기가 정확히 1건이고, 대변 원장 수가 반영된 건은 1·미반영 건은 0이어야 한다(중복·유실 0).
-- ledger_entries.transaction_id에는 인덱스가 없다. 이체마다 원장을 찾으면 1천만 건을 매번 훑으므로(s2f-r5에서 24분 넘게
-- 걸림), 경계 이후 원장(entry_id > b_le)만 한 번 모아 조인한다. 경계 이후 이체의 대변은 모두 경계 이후에 생긴다.
WITH t AS (
  SELECT transaction_id FROM transactions
  WHERE transaction_id > :b_tx AND transaction_type = 'TRANSFER' AND status = 'COMPLETED'
), p AS (
  SELECT transaction_id, count(*) AS n, bool_or(applied_at IS NOT NULL) AS applied
  FROM pending_credits WHERE transaction_id > :b_tx GROUP BY transaction_id
), c AS (
  SELECT transaction_id, count(*) AS credits FROM ledger_entries
  WHERE entry_id > :b_le AND entry_type = 'CREDIT' GROUP BY transaction_id
)
SELECT 'transfer_pending_row_mismatch', count(*) FROM t LEFT JOIN p USING (transaction_id) WHERE coalesce(p.n, 0) <> 1;
WITH t AS (
  SELECT transaction_id FROM transactions
  WHERE transaction_id > :b_tx AND transaction_type = 'TRANSFER' AND status = 'COMPLETED'
), p AS (
  SELECT transaction_id, bool_or(applied_at IS NOT NULL) AS applied
  FROM pending_credits WHERE transaction_id > :b_tx GROUP BY transaction_id
), c AS (
  SELECT transaction_id, count(*) AS credits FROM ledger_entries
  WHERE entry_id > :b_le AND entry_type = 'CREDIT' GROUP BY transaction_id
)
SELECT 'transfer_credit_entry_mismatch', count(*) FROM t
  LEFT JOIN p USING (transaction_id) LEFT JOIN c USING (transaction_id)
 WHERE coalesce(c.credits, 0) <> CASE WHEN coalesce(p.applied, false) THEN 1 ELSE 0 END;

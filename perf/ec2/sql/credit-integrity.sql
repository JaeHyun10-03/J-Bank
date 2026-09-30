-- 입금 비동기 반영(ADR 0012) 뒤의 정합성 추가 항목. target.sh integrity가 부르고, perf/ec2/tests/integrity-sql-test.sh가
-- 가짜 행으로 같은 파일을 검사한다. 변수: b_tx(경계 거래 ID), hot_id(핫 계좌 ID). 출력은 key=value 한 줄씩.
\pset format unaligned
\pset fieldsep '='
\pset tuples_only on
SELECT 'new_unapplied_count', count(*) FROM pending_credits WHERE transaction_id > :b_tx AND applied_at IS NULL;
SELECT 'new_unapplied_sum', coalesce(sum(amount), 0) FROM pending_credits WHERE transaction_id > :b_tx AND applied_at IS NULL;
SELECT 'hot_unapplied_sum', coalesce(sum(amount), 0) FROM pending_credits
 WHERE transaction_id > :b_tx AND applied_at IS NULL AND account_id = :hot_id;
SELECT 'all_unapplied_sum', coalesce(sum(amount), 0) FROM pending_credits WHERE applied_at IS NULL;
-- 완료 이체마다 입금 대기가 정확히 1건이고, 대변 원장 수가 반영된 건은 1·미반영 건은 0이어야 한다(중복·유실 0).
WITH t AS (
  SELECT tr.transaction_id,
         (SELECT count(*) FROM pending_credits p WHERE p.transaction_id = tr.transaction_id) AS pending_rows,
         (SELECT bool_or(p.applied_at IS NOT NULL) FROM pending_credits p WHERE p.transaction_id = tr.transaction_id) AS applied,
         (SELECT count(*) FROM ledger_entries l WHERE l.transaction_id = tr.transaction_id AND l.entry_type = 'CREDIT') AS credits
  FROM transactions tr
  WHERE tr.transaction_id > :b_tx AND tr.transaction_type = 'TRANSFER' AND tr.status = 'COMPLETED'
)
SELECT 'transfer_pending_row_mismatch', count(*) FROM t WHERE pending_rows <> 1;
WITH t AS (
  SELECT tr.transaction_id,
         (SELECT bool_or(p.applied_at IS NOT NULL) FROM pending_credits p WHERE p.transaction_id = tr.transaction_id) AS applied,
         (SELECT count(*) FROM ledger_entries l WHERE l.transaction_id = tr.transaction_id AND l.entry_type = 'CREDIT') AS credits
  FROM transactions tr
  WHERE tr.transaction_id > :b_tx AND tr.transaction_type = 'TRANSFER' AND tr.status = 'COMPLETED'
)
SELECT 'transfer_credit_entry_mismatch', count(*) FROM t
 WHERE credits <> CASE WHEN coalesce(applied, false) THEN 1 ELSE 0 END;

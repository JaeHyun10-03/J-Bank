-- 거래내역 조회(API-010) EXPLAIN 벤치마크 근거(perf/README.md W6 화요일분). from_account_id,
-- to_account_id 각각에 계좌id+거래id 복합 인덱스를 걸어 계좌별 최신순 조회가 인덱스 스캔만으로
-- 끝나게 한다. transaction_id DESC로 걸어 ORDER BY transaction_id DESC LIMIT 조회와 정렬 방향을
-- 맞췄다.
CREATE INDEX idx_transactions_from_account_id_transaction_id
    ON transactions (from_account_id, transaction_id DESC);
CREATE INDEX idx_transactions_to_account_id_transaction_id
    ON transactions (to_account_id, transaction_id DESC);

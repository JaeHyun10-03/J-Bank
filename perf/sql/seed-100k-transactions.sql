-- W4 목요일 몫: 인덱스 없는 상태의 거래내역 조회 성능 베이스라인을 남기기 위한 시드.
-- 기존 계좌들 사이에 트랜잭션 10만 건을 채운다. 실행 전 계좌가 최소 2개 있어야
-- 한다(SeedDataRunner의 시연용 계좌로 충분). 사용법:
--   psql "$DATABASE_URL" -f perf/sql/seed-100k-transactions.sql
--
-- ponytail: 계좌 id가 연속적이라고 가정한다(로컬 개발 DB 기준 삭제된 행이 없어
-- 성립). 중간에 빠진 id가 생기면 FK 위반이 나므로, 그때는 min/max 범위 계산
-- 대신 `OFFSET floor(random()*cnt) FROM accounts` 방식으로 바꾼다.
--
-- W6 화요일 몫에서 발견한 버그 수정: LATERAL 서브쿼리가 바깥 `g`를 전혀 참조하지
-- 않으면 PostgreSQL이 상관관계 없는 서브쿼리로 보고 무작위값을 딱 한 번만 계산해
-- 10만 행 전부에 같은 (거래유형, 계좌쌍)이 박히는 결과가 나온다. `WHERE g >= 1`은
-- 항상 참이라 결과에 영향은 없지만 `g`를 참조시켜 매 행 재평가를 강제한다.
WITH bounds AS (
    SELECT min(account_id) AS min_id, max(account_id) AS max_id, count(*) AS cnt
    FROM accounts
)
INSERT INTO transactions (
    transaction_type, from_account_id, to_account_id, amount,
    idempotency_key, status, memo, processed_at, created_at
)
SELECT
    t.transaction_type,
    CASE WHEN t.transaction_type = 'DEPOSIT' THEN NULL ELSE t.acc1 END,
    CASE WHEN t.transaction_type = 'WITHDRAWAL' THEN NULL ELSE t.acc2 END,
    round((random() * 990000 + 10000)::numeric, 2),
    'seed-100k-' || g,
    'COMPLETED',
    NULL,
    t.ts,
    t.ts
FROM bounds,
     generate_series(1, 100000) AS g,
     LATERAL (
         SELECT
             (ARRAY['DEPOSIT', 'WITHDRAWAL', 'TRANSFER'])[floor(random() * 3 + 1)::int]
                 AS transaction_type,
             bounds.min_id + floor(random() * bounds.cnt)::bigint AS acc1,
             bounds.min_id + floor(random() * bounds.cnt)::bigint AS acc2,
             now() - (random() * interval '365 days') AS ts
         WHERE g >= 1
     ) AS t;

-- 대규모 성능 측정용 시드. 계좌 10만 개(고객 1만 명 × 10개) 사이에 거래 1천만 건을 채운다.
-- 목적은 perf/README.md W6 화요일분에서 "로컬 계좌 3개로는 재현 불가"로 보류했던 병목들
-- (계좌당 매칭률이 낮을 때 PK 역순 스캔 붕괴, Page count(*) 비용, CTR 배치 seq scan)을
-- 실제로 드러내는 것이다. 계좌 10만 개면 계좌당 평균 매칭률이 약 0.002%로 떨어진다.
--
-- 사용법 (호스트에 psql이 없으면 컨테이너로):
--   docker compose -f infra/compose/docker-compose.yml exec -T postgres \
--     psql -U jbank -d jbank -v ON_ERROR_STOP=1 < perf/sql/seed-10m.sql
--
-- 선행 조건: Flyway 마이그레이션이 적용돼 있어야 한다(백엔드를 한 번 기동하면 됨).
-- SeedDataRunner의 시연용 고객/계좌 2개가 이미 있어도 무방하다 — 거래 풀에 함께 섞인다.
--
-- ponytail: account_id가 1부터 연속이라고 가정한다(seed-100k-transactions.sql과 동일한 전제).
-- 로컬 개발 DB에서 계좌 행을 지운 적이 없으면 성립한다. 중간에 빠진 id가 생기면 FK 위반이
-- 나므로, 그때는 min+random 범위 계산 대신 `ORDER BY random() LIMIT` 방식으로 바꾼다.
--
-- ponytail: 단독 로컬 DB 전용. 공유 DB에 돌리지 말 것 — 1천만 행을 넣고 인덱스를 재생성한다.

\set ON_ERROR_STOP on

-- 이미 시드가 돌아간 DB에 두 번 넣는 사고 방지.
DO $$
BEGIN
  IF (SELECT count(*) FROM transactions) > 1000 THEN
    RAISE EXCEPTION 'transactions에 이미 %건이 있어 시드를 중단한다. 초기화하려면 perf/README.md의 정리 절차 참고',
      (SELECT count(*) FROM transactions);
  END IF;
END $$;

-- 1. 고객 1만 명. 암호화 컬럼은 placeholder — 이 고객들은 로그인도 복호화도 하지 않는다.
INSERT INTO customers (
    name, resident_reg_no_encrypted, resident_reg_no_hash, birth_date,
    phone_encrypted, identity_verification_method, identity_verified_at,
    kyc_grade, aml_risk_level, status, login_id, password_hash, created_at, updated_at)
SELECT
    'seed-user-' || g,
    'enc-rrn-' || g,
    'hash-rrn-' || g,
    DATE '1970-01-01' + (g % 15000),
    'enc-phone-' || g,
    'FACE_TO_FACE',
    now(),
    'GENERAL',
    'LOW',
    'ACTIVE',
    'seed-user-' || g,
    '$2a$10$seedseedseedseedseedseSEEDSEEDSEEDSEEDSEEDSEEDSEEDSEEDSE',
    now(), now()
FROM generate_series(1, 10000) AS g;

-- 2. 계좌 10만 개. 방금 넣은 고객에게 10개씩 균등 배분.
WITH new_cust AS (
    SELECT customer_id, row_number() OVER (ORDER BY customer_id) AS rn
    FROM customers
    WHERE login_id LIKE 'seed-user-%'
)
INSERT INTO accounts (
    account_number, customer_id, account_type, status,
    current_balance_cache, hold_amount, opened_at, created_at, updated_at)
SELECT
    '900' || lpad(g::text, 10, '0'),
    nc.customer_id,
    'CHECKING',
    'ACTIVE',
    1000000000000.00,
    0,
    now(), now(), now()
FROM generate_series(1, 100000) AS g
JOIN new_cust nc ON nc.rn = ((g - 1) / 10) + 1;

-- 3. 인덱스를 떼고 벌크 적재 후 재생성한다. 1천만 행을 유지하며 넣는 것보다 몇 배 빠르다.
--    Flyway는 마이그레이션 파일 체크섬만 검증하므로 인덱스를 떼도 다음 기동에 영향 없다.
--    DDL은 V4/V13 마이그레이션과 동일하게 재생성한다.
DROP INDEX IF EXISTS idx_transactions_from_account_id_transaction_id;
DROP INDEX IF EXISTS idx_transactions_to_account_id_transaction_id;
DROP INDEX IF EXISTS uk_transactions_idempotency_key;

-- 4. 거래 1천만 건. 50만 건씩 20청크로 나눠 진행 상황을 로그로 남긴다.
--    LATERAL 서브쿼리의 `WHERE g >= 1`은 항상 참이지만 바깥 g를 참조시켜 매 행 재평가를
--    강제한다(참조가 없으면 PostgreSQL이 무작위값을 한 번만 계산해 전 행에 같은 값이 박힌다 —
--    seed-100k-transactions.sql에서 겪은 버그).
DO $$
DECLARE
    chunk_size int := 500000;
    total      int := 10000000;
    done       int := 0;
    min_id     bigint;
    cnt        bigint;
BEGIN
    SELECT min(account_id), count(*) INTO min_id, cnt FROM accounts;
    WHILE done < total LOOP
        INSERT INTO transactions (
            transaction_type, from_account_id, to_account_id, amount,
            idempotency_key, status, memo, processed_at, created_at)
        SELECT
            t.ttype,
            CASE WHEN t.ttype = 'DEPOSIT'    THEN NULL ELSE t.acc1 END,
            CASE WHEN t.ttype = 'WITHDRAWAL' THEN NULL ELSE t.acc2 END,
            round((random() * 990000 + 10000)::numeric, 2),
            'seed10m-' || (done + g),
            'COMPLETED',
            NULL,
            t.ts,
            t.ts
        FROM generate_series(1, chunk_size) AS g,
             LATERAL (
                 SELECT
                     (ARRAY['DEPOSIT', 'WITHDRAWAL', 'TRANSFER'])[floor(random() * 3 + 1)::int]
                         AS ttype,
                     min_id + floor(random() * cnt)::bigint AS acc1,
                     min_id + floor(random() * cnt)::bigint AS acc2,
                     now() - (random() * interval '365 days')  AS ts
                 WHERE g >= 1
             ) AS t;
        done := done + chunk_size;
        RAISE NOTICE 'transactions seeded: % / %', done, total;
    END LOOP;
END $$;

-- 5. 인덱스 재생성 + 통계 갱신.
CREATE UNIQUE INDEX uk_transactions_idempotency_key ON transactions (idempotency_key);
CREATE INDEX idx_transactions_from_account_id_transaction_id
    ON transactions (from_account_id, transaction_id DESC);
CREATE INDEX idx_transactions_to_account_id_transaction_id
    ON transactions (to_account_id, transaction_id DESC);

ANALYZE customers;
ANALYZE accounts;
ANALYZE transactions;

-- 이체의 수신 측 반영 대기(docs/adr/0012). 이체 트랜잭션이 송금 차감·차변 원장과 함께 한 행을
-- 넣고, 반영 워커가 수신 계좌별로 모아 대변 원장·잔액에 반영한 뒤 applied_at을 채운다.
-- 시각은 트랜잭션 시작 시각(now())이 아니라 실제 시각으로 남겨 반영 지연을 정확히 잰다.
CREATE TABLE pending_credits (
    pending_credit_id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL UNIQUE REFERENCES transactions (transaction_id),
    account_id BIGINT NOT NULL REFERENCES accounts (account_id),
    amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    applied_at TIMESTAMPTZ,
    CONSTRAINT chk_pending_credits_amount_positive CHECK (amount > 0)
);

-- 워커는 미반영 행만 계좌별로 찾는다. 반영이 끝난 행은 계속 쌓이지만 이 인덱스는 미반영 행 수에만
-- 비례한다(V9 outbox와 같은 방식).
CREATE INDEX idx_pending_credits_unapplied ON pending_credits (account_id, pending_credit_id)
    WHERE applied_at IS NULL;

CREATE TABLE transactions (
    transaction_id BIGSERIAL PRIMARY KEY,
    transaction_type VARCHAR(20) NOT NULL,
    from_account_id BIGINT REFERENCES accounts (account_id),
    to_account_id BIGINT REFERENCES accounts (account_id),
    amount NUMERIC(19, 2) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    memo VARCHAR(200),
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0)
);

-- 동시에 같은 키로 두 요청이 들어와도 두 번째 삽입을 DB가 막아준다(구현계획 4절 W2).
CREATE UNIQUE INDEX uk_transactions_idempotency_key ON transactions (idempotency_key);

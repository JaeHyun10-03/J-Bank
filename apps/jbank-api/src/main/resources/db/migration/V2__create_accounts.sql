CREATE TABLE accounts (
    account_id BIGSERIAL PRIMARY KEY,
    account_number VARCHAR(20) NOT NULL,
    customer_id BIGINT NOT NULL REFERENCES customers (customer_id),
    account_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    current_balance_cache NUMERIC(19, 2) NOT NULL DEFAULT 0,
    hold_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_accounts_hold_amount_range
        CHECK (hold_amount >= 0 AND hold_amount <= current_balance_cache)
);

CREATE UNIQUE INDEX uk_accounts_account_number ON accounts (account_number);
CREATE INDEX idx_accounts_customer_id ON accounts (customer_id);

-- 계좌번호 채번용 시퀀스. 지점코드-시퀀스-체크디지트 조합의 가운데 부분을 여기서 원자적으로 채번한다.
CREATE SEQUENCE account_number_seq START WITH 1 INCREMENT BY 1;

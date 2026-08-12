CREATE TABLE ctr_report_queue (
    report_id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers (customer_id),
    account_id BIGINT NOT NULL REFERENCES accounts (account_id),
    transaction_date DATE NOT NULL,
    total_cash_amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 같은 기준일 배치를 재실행해도 같은 계좌·거래일 조합이 중복 적재되지 않게 막는다(구현계획 W5).
CREATE UNIQUE INDEX uk_ctr_report_queue_customer_account_date
    ON ctr_report_queue (customer_id, account_id, transaction_date);

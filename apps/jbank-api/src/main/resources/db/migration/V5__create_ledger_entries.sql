CREATE TABLE ledger_entries (
    entry_id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts (account_id),
    transaction_id BIGINT NOT NULL REFERENCES transactions (transaction_id),
    entry_type VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    balance_after_snapshot NUMERIC(19, 2) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ledger_entries_amount_positive CHECK (amount > 0)
);

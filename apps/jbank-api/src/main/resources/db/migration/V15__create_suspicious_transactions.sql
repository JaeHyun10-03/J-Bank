-- 룰 기반 이상거래 탐지(FR-SUP-003, API-022, 구현계획 W7). ERD 문서에 없던
-- 신규 테이블 — CTR과 달리 "보고"가 아니라 운영자 조회용 결과 적재라
-- status 컬럼이 없다(ctr_report_queue와 다른 점).
CREATE TABLE suspicious_transactions (
    suspicious_transaction_id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES transactions (transaction_id),
    account_id BIGINT NOT NULL REFERENCES accounts (account_id),
    rule_type VARCHAR(40) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    detail VARCHAR(200) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 같은 배치를 재실행해도 같은 거래·룰 조합이 중복 적재되지 않게 막는다(CTR과 같은 패턴).
CREATE UNIQUE INDEX uk_suspicious_transactions_transaction_rule
    ON suspicious_transactions (transaction_id, rule_type);

CREATE INDEX idx_suspicious_transactions_detected_at ON suspicious_transactions (detected_at);

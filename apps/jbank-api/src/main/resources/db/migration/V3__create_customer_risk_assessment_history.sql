CREATE TABLE customer_risk_assessment_history (
    assessment_id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers (customer_id),
    kyc_grade VARCHAR(20) NOT NULL,
    aml_risk_level VARCHAR(10) NOT NULL,
    transaction_purpose VARCHAR(200),
    fund_source VARCHAR(200),
    assessed_by VARCHAR(50) NOT NULL,
    assessed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_risk_assessment_history_customer_id
    ON customer_risk_assessment_history (customer_id);

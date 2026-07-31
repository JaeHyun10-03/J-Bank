CREATE TABLE customers (
    customer_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    resident_reg_no_encrypted VARCHAR(255) NOT NULL,
    resident_reg_no_hash VARCHAR(64) NOT NULL,
    birth_date DATE NOT NULL,
    phone_encrypted VARCHAR(255) NOT NULL,
    address_encrypted VARCHAR(500),
    occupation VARCHAR(100),
    identity_verification_method VARCHAR(20) NOT NULL,
    identity_verified_at TIMESTAMPTZ NOT NULL,
    kyc_grade VARCHAR(20) NOT NULL,
    aml_risk_level VARCHAR(10) NOT NULL,
    transaction_purpose VARCHAR(200),
    fund_source VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_customers_resident_reg_no_hash ON customers (resident_reg_no_hash);

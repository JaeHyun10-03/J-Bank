-- ERD 문서 2.5절. status 컬럼은 ERD 표에는 없지만 PRD_002_PRODUCT_NOT_AVAILABLE(판매중지
-- 상품 가입 거부)를 판별하려면 필요해 이번에 추가한다.
CREATE TABLE products (
    product_code VARCHAR(30) PRIMARY KEY,
    product_name VARCHAR(100) NOT NULL,
    interest_rate NUMERIC(5, 4) NOT NULL,
    min_subscription_amount NUMERIC(19, 2) NOT NULL,
    contract_period_months INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE'
);

CREATE TABLE product_contracts (
    contract_id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers (customer_id),
    product_code VARCHAR(30) NOT NULL REFERENCES products (product_code),
    account_id BIGINT NOT NULL REFERENCES accounts (account_id),
    subscription_amount NUMERIC(19, 2) NOT NULL,
    subscribed_at TIMESTAMPTZ NOT NULL,
    maturity_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_contracts_customer_id ON product_contracts (customer_id);

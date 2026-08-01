-- ERD 문서에 로그인 자격증명 컬럼이 빠져 있었다. API-011 로그인이 loginId/password를
-- 요구하므로(API설계 2.2·4절) 여기서 채워 넣는다(ADR 0004 참고).
ALTER TABLE customers
    ADD COLUMN login_id VARCHAR(50) NOT NULL,
    ADD COLUMN password_hash VARCHAR(255) NOT NULL;

CREATE UNIQUE INDEX uk_customers_login_id ON customers (login_id);

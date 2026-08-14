-- 만료 대기 거래 정리 스케줄러가 주기적으로 조회하는 대상이다(ERD 3절). 테이블이 커질수록
-- 비용이 나빠지는 폴링 조회라 데이터가 적을 때 미리 넣어둔다.
CREATE INDEX idx_transactions_pending_otp_created_at ON transactions (status, created_at)
    WHERE status = 'PENDING_OTP';

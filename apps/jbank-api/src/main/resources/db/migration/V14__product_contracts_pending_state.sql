-- W7 product 모듈 분리. 상품가입이 오케스트레이션 사가(계약 생성 PENDING → 초기
-- 납입금 출금 → 계약 확정 ACTIVE)로 바뀌면서, 계약 행을 출금 성공 여부를 알기
-- 전에 먼저 만들어야 한다 — 그래야 서비스가 출금 직후·확정 직전에 죽어도
-- "돈은 나갔는데 계약도 어디에도 안 남는" 상태 대신 PENDING 행으로 사가가
-- 중간에 멈췄다는 사실 자체가 남는다. accountId는 그 시점엔 아직 모르므로
-- NOT NULL을 풀어야 한다.
ALTER TABLE product_contracts ALTER COLUMN account_id DROP NOT NULL;

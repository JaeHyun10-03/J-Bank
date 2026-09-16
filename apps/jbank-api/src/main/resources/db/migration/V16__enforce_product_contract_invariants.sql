-- 계약·출금이 한 트랜잭션에서 확정되므로 계좌와 최종 계약 상태가 반드시 존재해야 한다.
-- 호환되지 않는 기존 행은 금전 처리 여부를 자동 추정하거나 삭제하지 않고 검증에서 중단한다.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM product_contracts
        WHERE account_id IS NULL OR status NOT IN ('ACTIVE', 'MATURED', 'TERMINATED')
    ) THEN
        RAISE EXCEPTION 'product_contracts contains unresolved contracts; reconcile their monetary outcome before migrating';
    END IF;
END $$;

ALTER TABLE product_contracts ALTER COLUMN account_id SET NOT NULL;
ALTER TABLE product_contracts ADD CONSTRAINT chk_product_contracts_status
    CHECK (status IN ('ACTIVE', 'MATURED', 'TERMINATED'));

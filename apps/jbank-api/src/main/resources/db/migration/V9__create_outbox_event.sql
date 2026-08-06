CREATE TABLE outbox_event (
    event_id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

-- 폴링 발행기가 매 주기마다 미발행 레코드만 골라 조회하므로, status가 PENDING인
-- 행만 담는 부분 인덱스를 건다(ERD 2.9절). 발행이 끝난 레코드는 계속 쌓이지만
-- 이 인덱스 크기는 미발행 행 수에만 비례해 거의 비어 있는 상태로 유지된다.
CREATE INDEX idx_outbox_pending ON outbox_event (occurred_at) WHERE status = 'PENDING';

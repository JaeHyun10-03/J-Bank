-- 사용하지 않는 로그 알림 대기 테이블을 제거한다. 거래 원장과 감사 기록에는 영향이 없다.
DROP TABLE IF EXISTS outbox_event;

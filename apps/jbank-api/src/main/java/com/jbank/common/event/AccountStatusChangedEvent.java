package com.jbank.common.event;

import java.time.OffsetDateTime;

/**
 * 계좌 상태 변경 이벤트. common은 어떤 도메인 패키지도 의존하지 않아야 해서(폴더구조 4.1절) 상태값은 도메인 enum이 아니라 name()으로 직렬화한 문자열로
 * 담는다.
 */
public record AccountStatusChangedEvent(
    Long accountId, String previousStatus, String newStatus, OffsetDateTime occurredAt) {}

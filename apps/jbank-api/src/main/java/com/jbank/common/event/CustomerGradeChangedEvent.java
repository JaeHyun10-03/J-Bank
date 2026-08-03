package com.jbank.common.event;

import java.time.OffsetDateTime;

/** 등급 변경 이벤트. common이 customer 도메인 enum을 의존하지 않도록 문자열로 담는다. */
public record CustomerGradeChangedEvent(
    Long customerId,
    String previousKycGrade,
    String newKycGrade,
    String previousAmlRiskLevel,
    String newAmlRiskLevel,
    String assessedBy,
    OffsetDateTime occurredAt) {}

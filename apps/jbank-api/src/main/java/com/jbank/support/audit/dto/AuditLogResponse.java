package com.jbank.support.audit.dto;

import com.jbank.support.audit.domain.ActorType;
import com.jbank.support.audit.domain.AuditLog;
import java.time.OffsetDateTime;
import java.util.Map;

public record AuditLogResponse(
    Long logId,
    String eventType,
    ActorType actorType,
    String actorId,
    String targetType,
    String targetId,
    Map<String, Object> detail,
    OffsetDateTime occurredAt) {

  public static AuditLogResponse from(AuditLog auditLog) {
    return new AuditLogResponse(
        auditLog.getLogId(),
        auditLog.getEventType(),
        auditLog.getActorType(),
        auditLog.getActorId(),
        auditLog.getTargetType(),
        auditLog.getTargetId(),
        auditLog.getDetail(),
        auditLog.getOccurredAt());
  }
}

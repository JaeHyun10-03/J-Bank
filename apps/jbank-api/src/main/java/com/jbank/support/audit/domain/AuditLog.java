package com.jbank.support.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_log")
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "log_id")
  private Long logId;

  @Column(name = "event_type", nullable = false, length = 50)
  private String eventType;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor_type", nullable = false, length = 20)
  private ActorType actorType;

  @Column(name = "actor_id", length = 50)
  private String actorId;

  @Column(name = "target_type", nullable = false, length = 50)
  private String targetType;

  @Column(name = "target_id", nullable = false, length = 50)
  private String targetId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "detail")
  private Map<String, Object> detail;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt;

  protected AuditLog() {}

  public AuditLog(
      String eventType,
      ActorType actorType,
      String actorId,
      String targetType,
      String targetId,
      Map<String, Object> detail,
      OffsetDateTime occurredAt) {
    this.eventType = eventType;
    this.actorType = actorType;
    this.actorId = actorId;
    this.targetType = targetType;
    this.targetId = targetId;
    this.detail = detail;
    this.occurredAt = occurredAt;
  }

  public Long getLogId() {
    return logId;
  }

  public String getEventType() {
    return eventType;
  }

  public ActorType getActorType() {
    return actorType;
  }

  public String getActorId() {
    return actorId;
  }

  public String getTargetType() {
    return targetType;
  }

  public String getTargetId() {
    return targetId;
  }

  public Map<String, Object> getDetail() {
    return detail;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
  }
}

package com.jbank.support.outbox.domain;

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
@Table(name = "outbox_event")
public class OutboxEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "event_id")
  private Long eventId;

  @Column(name = "aggregate_type", nullable = false, length = 50)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false, length = 50)
  private String aggregateId;

  @Column(name = "event_type", nullable = false, length = 50)
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false)
  private Map<String, Object> payload;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private OutboxEventStatus status;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt;

  @Column(name = "published_at")
  private OffsetDateTime publishedAt;

  protected OutboxEvent() {}

  public OutboxEvent(
      String aggregateType,
      String aggregateId,
      String eventType,
      Map<String, Object> payload,
      OffsetDateTime occurredAt) {
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
    this.status = OutboxEventStatus.PENDING;
    this.retryCount = 0;
    this.occurredAt = occurredAt;
  }

  public void markPublished(OffsetDateTime publishedAt) {
    this.status = OutboxEventStatus.PUBLISHED;
    this.publishedAt = publishedAt;
  }

  // ponytail: 발행 실패는 PENDING을 유지해 다음 폴링 주기에 재시도한다(최소 한 번 전달 보장).
  // 재시도 상한을 넘겨 FAILED로 terminate하는 로직은 아직 없음 — 필요해지면 추가.
  public void markFailed(String errorMessage) {
    this.retryCount++;
    this.lastError =
        errorMessage != null && errorMessage.length() > 500
            ? errorMessage.substring(0, 500)
            : errorMessage;
  }

  public Long getEventId() {
    return eventId;
  }

  public String getAggregateType() {
    return aggregateType;
  }

  public String getAggregateId() {
    return aggregateId;
  }

  public String getEventType() {
    return eventType;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }

  public OutboxEventStatus getStatus() {
    return status;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public String getLastError() {
    return lastError;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
  }

  public OffsetDateTime getPublishedAt() {
    return publishedAt;
  }
}

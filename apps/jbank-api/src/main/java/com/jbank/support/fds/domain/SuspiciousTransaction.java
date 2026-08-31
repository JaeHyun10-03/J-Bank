package com.jbank.support.fds.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 룰 기반 이상거래 탐지 결과(ERD에 없던 신규 테이블, FR-SUP-003·API-022). 실제 대응은 운영자가 조회해서 판단한다 — 자동 차단은 없다. */
@Entity
@Table(name = "suspicious_transactions")
public class SuspiciousTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "suspicious_transaction_id")
  private Long suspiciousTransactionId;

  @Column(name = "transaction_id", nullable = false)
  private Long transactionId;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "rule_type", nullable = false, length = 40)
  private FdsRuleType ruleType;

  @Column(name = "amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(name = "detail", nullable = false, length = 200)
  private String detail;

  @Column(name = "detected_at", nullable = false)
  private OffsetDateTime detectedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected SuspiciousTransaction() {}

  public SuspiciousTransaction(
      Long transactionId,
      Long accountId,
      FdsRuleType ruleType,
      BigDecimal amount,
      String detail,
      OffsetDateTime detectedAt) {
    this.transactionId = transactionId;
    this.accountId = accountId;
    this.ruleType = ruleType;
    this.amount = amount;
    this.detail = detail;
    this.detectedAt = detectedAt;
    this.createdAt = OffsetDateTime.now();
  }

  public Long getSuspiciousTransactionId() {
    return suspiciousTransactionId;
  }

  public Long getTransactionId() {
    return transactionId;
  }

  public Long getAccountId() {
    return accountId;
  }

  public FdsRuleType getRuleType() {
    return ruleType;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getDetail() {
    return detail;
  }

  public OffsetDateTime getDetectedAt() {
    return detectedAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}

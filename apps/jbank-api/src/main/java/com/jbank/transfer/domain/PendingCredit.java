package com.jbank.transfer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 이체의 수신 측 반영 대기(docs/adr/0012). 이체 트랜잭션이 송금 차감·차변 원장과 함께 만들고, 반영 워커가 수신 계좌 잔액·대변 원장에 반영한 뒤
 * applied_at을 채운다. 시각은 DB의 clock_timestamp()가 넣는다.
 */
@Entity
@Table(name = "pending_credits")
public class PendingCredit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "pending_credit_id")
  private Long pendingCreditId;

  @Column(name = "transaction_id", nullable = false, unique = true)
  private Long transactionId;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(name = "created_at", insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "applied_at", insertable = false, updatable = false)
  private OffsetDateTime appliedAt;

  protected PendingCredit() {}

  public PendingCredit(Long transactionId, Long accountId, BigDecimal amount) {
    this.transactionId = transactionId;
    this.accountId = accountId;
    this.amount = amount;
  }

  public Long getPendingCreditId() {
    return pendingCreditId;
  }

  public Long getTransactionId() {
    return transactionId;
  }

  public Long getAccountId() {
    return accountId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getAppliedAt() {
    return appliedAt;
  }
}

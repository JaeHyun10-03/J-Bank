package com.jbank.transfer.domain;

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

@Entity
@Table(name = "transactions")
public class Transaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "transaction_id")
  private Long transactionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "transaction_type", nullable = false, length = 20)
  private TransactionType transactionType;

  @Column(name = "from_account_id")
  private Long fromAccountId;

  @Column(name = "to_account_id")
  private Long toAccountId;

  @Column(name = "amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
  private String idempotencyKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private TransactionStatus status;

  @Column(name = "memo", length = 200)
  private String memo;

  @Column(name = "processed_at")
  private OffsetDateTime processedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected Transaction() {}

  public Transaction(
      TransactionType transactionType,
      Long fromAccountId,
      Long toAccountId,
      BigDecimal amount,
      String idempotencyKey,
      String memo) {
    this.transactionType = transactionType;
    this.fromAccountId = fromAccountId;
    this.toAccountId = toAccountId;
    this.amount = amount;
    this.idempotencyKey = idempotencyKey;
    this.memo = memo;
    this.status = TransactionStatus.PENDING;
    this.createdAt = OffsetDateTime.now();
  }

  public void complete(OffsetDateTime processedAt) {
    transitionTo(TransactionStatus.COMPLETED);
    this.processedAt = processedAt;
  }

  public void fail() {
    transitionTo(TransactionStatus.FAILED);
  }

  private void transitionTo(TransactionStatus target) {
    if (!status.canTransitionTo(target)) {
      throw new IllegalStateException("허용되지 않는 거래 상태 전이: " + status + " -> " + target);
    }
    this.status = target;
  }

  public Long getTransactionId() {
    return transactionId;
  }

  public TransactionType getTransactionType() {
    return transactionType;
  }

  public Long getFromAccountId() {
    return fromAccountId;
  }

  public Long getToAccountId() {
    return toAccountId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public TransactionStatus getStatus() {
    return status;
  }

  public String getMemo() {
    return memo;
  }

  public OffsetDateTime getProcessedAt() {
    return processedAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}

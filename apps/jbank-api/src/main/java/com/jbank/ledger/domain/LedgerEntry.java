package com.jbank.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ledger_entries")
@EntityListeners(LedgerEntryImmutabilityGuard.class)
public class LedgerEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "entry_id")
  private Long entryId;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "transaction_id", nullable = false)
  private Long transactionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type", nullable = false, length = 10)
  private EntryType entryType;

  @Column(name = "amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(name = "balance_after_snapshot", nullable = false, precision = 19, scale = 2)
  private BigDecimal balanceAfterSnapshot;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected LedgerEntry() {}

  public LedgerEntry(
      Long accountId,
      Long transactionId,
      EntryType entryType,
      BigDecimal amount,
      BigDecimal balanceAfterSnapshot,
      OffsetDateTime occurredAt) {
    this.accountId = accountId;
    this.transactionId = transactionId;
    this.entryType = entryType;
    this.amount = amount;
    this.balanceAfterSnapshot = balanceAfterSnapshot;
    this.occurredAt = occurredAt;
    this.createdAt = OffsetDateTime.now();
  }

  public Long getEntryId() {
    return entryId;
  }

  public Long getAccountId() {
    return accountId;
  }

  public Long getTransactionId() {
    return transactionId;
  }

  public EntryType getEntryType() {
    return entryType;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public BigDecimal getBalanceAfterSnapshot() {
    return balanceAfterSnapshot;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}

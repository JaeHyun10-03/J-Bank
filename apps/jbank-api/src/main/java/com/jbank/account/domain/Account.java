package com.jbank.account.domain;

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
@Table(name = "accounts")
public class Account {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "account_id")
  private Long accountId;

  @Column(name = "account_number", nullable = false, unique = true, length = 20)
  private String accountNumber;

  @Column(name = "customer_id", nullable = false)
  private Long customerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "account_type", nullable = false, length = 20)
  private AccountType accountType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private AccountStatus status;

  @Column(name = "current_balance_cache", nullable = false, precision = 19, scale = 2)
  private BigDecimal currentBalanceCache;

  @Column(name = "hold_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal holdAmount;

  @Column(name = "opened_at", nullable = false)
  private OffsetDateTime openedAt;

  @Column(name = "closed_at")
  private OffsetDateTime closedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected Account() {}

  public Account(
      String accountNumber,
      Long customerId,
      AccountType accountType,
      AccountStatus status,
      BigDecimal currentBalanceCache,
      BigDecimal holdAmount,
      OffsetDateTime openedAt) {
    this.accountNumber = accountNumber;
    this.customerId = customerId;
    this.accountType = accountType;
    this.status = status;
    this.currentBalanceCache = currentBalanceCache;
    this.holdAmount = holdAmount;
    this.openedAt = openedAt;
    OffsetDateTime now = OffsetDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void changeStatus(AccountStatus newStatus) {
    this.status = newStatus;
    this.updatedAt = OffsetDateTime.now();
  }

  public Long getAccountId() {
    return accountId;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public Long getCustomerId() {
    return customerId;
  }

  public AccountType getAccountType() {
    return accountType;
  }

  public AccountStatus getStatus() {
    return status;
  }

  public BigDecimal getCurrentBalanceCache() {
    return currentBalanceCache;
  }

  public BigDecimal getHoldAmount() {
    return holdAmount;
  }

  public OffsetDateTime getOpenedAt() {
    return openedAt;
  }

  public OffsetDateTime getClosedAt() {
    return closedAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}

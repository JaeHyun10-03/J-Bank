package com.jbank.support.ctr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 고액현금거래(CTR) 보고대상 판별 결과를 적재하는 큐(ERD 2.7절, FR-SUP-004). 실제 전송은 로그 출력으로 대체한다. */
@Entity
@Table(name = "ctr_report_queue")
public class CtrReportQueue {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "report_id")
  private Long reportId;

  @Column(name = "customer_id", nullable = false)
  private Long customerId;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "transaction_date", nullable = false)
  private LocalDate transactionDate;

  @Column(name = "total_cash_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalCashAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private CtrReportStatus status;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected CtrReportQueue() {}

  public CtrReportQueue(
      Long customerId, Long accountId, LocalDate transactionDate, BigDecimal totalCashAmount) {
    this.customerId = customerId;
    this.accountId = accountId;
    this.transactionDate = transactionDate;
    this.totalCashAmount = totalCashAmount;
    this.status = CtrReportStatus.PENDING;
    this.createdAt = OffsetDateTime.now();
  }

  public void markLogged() {
    this.status = CtrReportStatus.LOGGED;
  }

  public Long getReportId() {
    return reportId;
  }

  public Long getCustomerId() {
    return customerId;
  }

  public Long getAccountId() {
    return accountId;
  }

  public LocalDate getTransactionDate() {
    return transactionDate;
  }

  public BigDecimal getTotalCashAmount() {
    return totalCashAmount;
  }

  public CtrReportStatus getStatus() {
    return status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}

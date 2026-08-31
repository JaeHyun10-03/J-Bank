package com.jbank.product.domain;

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
@Table(name = "product_contracts")
public class ProductContract {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "contract_id")
  private Long contractId;

  @Column(name = "customer_id", nullable = false)
  private Long customerId;

  @Column(name = "product_code", nullable = false, length = 30)
  private String productCode;

  // 사가가 PENDING 단계일 땐 아직 모른다 — 출금 응답으로 accountNumber가 어느
  // accountId인지 확인된 뒤에야 채워진다(V14 마이그레이션으로 NOT NULL 해제).
  @Column(name = "account_id")
  private Long accountId;

  @Column(name = "subscription_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal subscriptionAmount;

  @Column(name = "subscribed_at", nullable = false)
  private OffsetDateTime subscribedAt;

  @Column(name = "maturity_at", nullable = false)
  private OffsetDateTime maturityAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ContractStatus status;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected ProductContract() {}

  /** 사가 1단계 — 출금 전에 먼저 PENDING으로 만든다. 서비스가 죽어도 사가가 중간에 멈췄다는 흔적이 남도록. */
  public static ProductContract pending(
      Long customerId,
      String productCode,
      BigDecimal subscriptionAmount,
      OffsetDateTime subscribedAt,
      OffsetDateTime maturityAt) {
    return new ProductContract(
        customerId,
        productCode,
        null,
        subscriptionAmount,
        subscribedAt,
        maturityAt,
        ContractStatus.PENDING);
  }

  /** 사가 3단계 — 출금이 성공해 계약을 확정한다. */
  public void confirm(Long accountId) {
    requireStatus(ContractStatus.PENDING, "confirm");
    this.accountId = accountId;
    this.status = ContractStatus.ACTIVE;
  }

  /** 출금은 성공했지만 확정이 실패해 보상 거래(출금 롤백)로 되돌린 뒤 이 상태로 남긴다(감사 기록, 삭제하지 않음). */
  public void markFailed(Long accountId) {
    requireStatus(ContractStatus.PENDING, "markFailed");
    this.accountId = accountId;
    this.status = ContractStatus.FAILED;
  }

  public void markMatured() {
    requireStatus(ContractStatus.ACTIVE, "markMatured");
    this.status = ContractStatus.MATURED;
  }

  private void requireStatus(ContractStatus required, String action) {
    if (status != required) {
      throw new IllegalStateException(
          "허용되지 않는 계약 상태 전이(" + action + "): " + status + " -> 필요 상태 " + required);
    }
  }

  public ProductContract(
      Long customerId,
      String productCode,
      Long accountId,
      BigDecimal subscriptionAmount,
      OffsetDateTime subscribedAt,
      OffsetDateTime maturityAt,
      ContractStatus status) {
    this.customerId = customerId;
    this.productCode = productCode;
    this.accountId = accountId;
    this.subscriptionAmount = subscriptionAmount;
    this.subscribedAt = subscribedAt;
    this.maturityAt = maturityAt;
    this.status = status;
    this.createdAt = OffsetDateTime.now();
  }

  public Long getContractId() {
    return contractId;
  }

  public Long getCustomerId() {
    return customerId;
  }

  public String getProductCode() {
    return productCode;
  }

  public Long getAccountId() {
    return accountId;
  }

  public BigDecimal getSubscriptionAmount() {
    return subscriptionAmount;
  }

  public OffsetDateTime getSubscribedAt() {
    return subscribedAt;
  }

  public OffsetDateTime getMaturityAt() {
    return maturityAt;
  }

  public ContractStatus getStatus() {
    return status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}

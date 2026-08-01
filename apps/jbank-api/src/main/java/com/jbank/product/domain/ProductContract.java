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

  @Column(name = "account_id", nullable = false)
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

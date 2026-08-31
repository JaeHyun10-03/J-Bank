package com.jbank.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {

  @Id
  @Column(name = "product_code", length = 30)
  private String productCode;

  @Column(name = "product_name", nullable = false, length = 100)
  private String productName;

  @Column(name = "interest_rate", nullable = false, precision = 5, scale = 4)
  private BigDecimal interestRate;

  @Column(name = "min_subscription_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal minSubscriptionAmount;

  @Column(name = "contract_period_months", nullable = false)
  private int contractPeriodMonths;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ProductStatus status;

  protected Product() {}

  public Product(
      String productCode,
      String productName,
      BigDecimal interestRate,
      BigDecimal minSubscriptionAmount,
      int contractPeriodMonths,
      ProductStatus status) {
    this.productCode = productCode;
    this.productName = productName;
    this.interestRate = interestRate;
    this.minSubscriptionAmount = minSubscriptionAmount;
    this.contractPeriodMonths = contractPeriodMonths;
    this.status = status;
  }

  public String getProductCode() {
    return productCode;
  }

  public String getProductName() {
    return productName;
  }

  public BigDecimal getInterestRate() {
    return interestRate;
  }

  public BigDecimal getMinSubscriptionAmount() {
    return minSubscriptionAmount;
  }

  public int getContractPeriodMonths() {
    return contractPeriodMonths;
  }

  public ProductStatus getStatus() {
    return status;
  }
}

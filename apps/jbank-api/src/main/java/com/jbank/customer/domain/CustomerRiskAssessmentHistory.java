package com.jbank.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_risk_assessment_history")
public class CustomerRiskAssessmentHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_id")
  private Long assessmentId;

  @Column(name = "customer_id", nullable = false)
  private Long customerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "kyc_grade", nullable = false, length = 20)
  private KycGrade kycGrade;

  @Enumerated(EnumType.STRING)
  @Column(name = "aml_risk_level", nullable = false, length = 10)
  private RiskLevel amlRiskLevel;

  @Column(name = "transaction_purpose", length = 200)
  private String transactionPurpose;

  @Column(name = "fund_source", length = 200)
  private String fundSource;

  @Column(name = "assessed_by", nullable = false, length = 50)
  private String assessedBy;

  @Column(name = "assessed_at", nullable = false)
  private OffsetDateTime assessedAt;

  protected CustomerRiskAssessmentHistory() {}

  public CustomerRiskAssessmentHistory(
      Long customerId,
      KycGrade kycGrade,
      RiskLevel amlRiskLevel,
      String transactionPurpose,
      String fundSource,
      String assessedBy) {
    this.customerId = customerId;
    this.kycGrade = kycGrade;
    this.amlRiskLevel = amlRiskLevel;
    this.transactionPurpose = transactionPurpose;
    this.fundSource = fundSource;
    this.assessedBy = assessedBy;
    this.assessedAt = OffsetDateTime.now();
  }

  public Long getAssessmentId() {
    return assessmentId;
  }

  public Long getCustomerId() {
    return customerId;
  }

  public KycGrade getKycGrade() {
    return kycGrade;
  }

  public RiskLevel getAmlRiskLevel() {
    return amlRiskLevel;
  }

  public String getTransactionPurpose() {
    return transactionPurpose;
  }

  public String getFundSource() {
    return fundSource;
  }

  public String getAssessedBy() {
    return assessedBy;
  }

  public OffsetDateTime getAssessedAt() {
    return assessedAt;
  }
}

package com.jbank.customer.domain;

import com.jbank.common.crypto.AesGcmAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "customers")
public class Customer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "customer_id")
  private Long customerId;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "login_id", nullable = false, unique = true, length = 50)
  private String loginId;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Convert(converter = AesGcmAttributeConverter.class)
  @Column(name = "resident_reg_no_encrypted", nullable = false, length = 255)
  private String residentRegNo;

  @Column(name = "resident_reg_no_hash", nullable = false, unique = true, length = 64)
  private String residentRegNoHash;

  @Column(name = "birth_date", nullable = false)
  private LocalDate birthDate;

  @Convert(converter = AesGcmAttributeConverter.class)
  @Column(name = "phone_encrypted", nullable = false, length = 255)
  private String phone;

  @Convert(converter = AesGcmAttributeConverter.class)
  @Column(name = "address_encrypted", length = 500)
  private String address;

  @Column(name = "occupation", length = 100)
  private String occupation;

  @Enumerated(EnumType.STRING)
  @Column(name = "identity_verification_method", nullable = false, length = 20)
  private IdentityVerificationMethod identityVerificationMethod;

  @Column(name = "identity_verified_at", nullable = false)
  private OffsetDateTime identityVerifiedAt;

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

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private CustomerStatus status;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected Customer() {}

  public Customer(
      String name,
      String loginId,
      String passwordHash,
      String residentRegNo,
      String residentRegNoHash,
      LocalDate birthDate,
      String phone,
      String address,
      String occupation,
      IdentityVerificationMethod identityVerificationMethod,
      OffsetDateTime identityVerifiedAt,
      KycGrade kycGrade,
      RiskLevel amlRiskLevel,
      String transactionPurpose,
      String fundSource,
      CustomerStatus status) {
    this.name = name;
    this.loginId = loginId;
    this.passwordHash = passwordHash;
    this.residentRegNo = residentRegNo;
    this.residentRegNoHash = residentRegNoHash;
    this.birthDate = birthDate;
    this.phone = phone;
    this.address = address;
    this.occupation = occupation;
    this.identityVerificationMethod = identityVerificationMethod;
    this.identityVerifiedAt = identityVerifiedAt;
    this.kycGrade = kycGrade;
    this.amlRiskLevel = amlRiskLevel;
    this.transactionPurpose = transactionPurpose;
    this.fundSource = fundSource;
    this.status = status;
    OffsetDateTime now = OffsetDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void recordEddConfirmation(String transactionPurpose, String fundSource) {
    this.transactionPurpose = transactionPurpose;
    this.fundSource = fundSource;
    this.updatedAt = OffsetDateTime.now();
  }

  public Long getCustomerId() {
    return customerId;
  }

  public String getName() {
    return name;
  }

  public String getLoginId() {
    return loginId;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getResidentRegNo() {
    return residentRegNo;
  }

  public String getResidentRegNoHash() {
    return residentRegNoHash;
  }

  public LocalDate getBirthDate() {
    return birthDate;
  }

  public String getPhone() {
    return phone;
  }

  public String getAddress() {
    return address;
  }

  public String getOccupation() {
    return occupation;
  }

  public IdentityVerificationMethod getIdentityVerificationMethod() {
    return identityVerificationMethod;
  }

  public OffsetDateTime getIdentityVerifiedAt() {
    return identityVerifiedAt;
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

  public CustomerStatus getStatus() {
    return status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}

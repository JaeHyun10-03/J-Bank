package com.jbank.customer.service;

import com.jbank.common.crypto.ResidentRegNoHasher;
import com.jbank.customer.domain.CddAssessmentResult;
import com.jbank.customer.domain.CddGradeCalculator;
import com.jbank.customer.domain.Customer;
import com.jbank.customer.domain.CustomerException;
import com.jbank.customer.domain.CustomerRiskAssessmentHistory;
import com.jbank.customer.domain.CustomerStatus;
import com.jbank.customer.domain.KycGrade;
import com.jbank.customer.domain.RiskLevel;
import com.jbank.customer.dto.CustomerRegisterRequest;
import com.jbank.customer.dto.CustomerRegisterResponse;
import com.jbank.customer.dto.EddRegisterRequest;
import com.jbank.customer.dto.EddRegisterResponse;
import com.jbank.customer.repository.CustomerRepository;
import com.jbank.customer.repository.CustomerRiskAssessmentHistoryRepository;
import com.jbank.global.exception.ErrorCode;
import java.time.OffsetDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

  private static final String ASSESSED_BY_SYSTEM = "SYSTEM";

  private final CustomerRepository customerRepository;
  private final CustomerRiskAssessmentHistoryRepository historyRepository;
  private final PasswordEncoder passwordEncoder;

  public CustomerService(
      CustomerRepository customerRepository,
      CustomerRiskAssessmentHistoryRepository historyRepository,
      PasswordEncoder passwordEncoder) {
    this.customerRepository = customerRepository;
    this.historyRepository = historyRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public CustomerRegisterResponse register(CustomerRegisterRequest request) {
    String residentRegNoHash = ResidentRegNoHasher.hash(request.residentRegNo());
    if (customerRepository.findByResidentRegNoHash(residentRegNoHash).isPresent()) {
      throw new CustomerException(ErrorCode.ACC_001_DUPLICATE_RESIDENT_REG_NO);
    }
    if (customerRepository.findByLoginId(request.loginId()).isPresent()) {
      throw new CustomerException(ErrorCode.ACC_011_DUPLICATE_LOGIN_ID);
    }

    // ponytail: 직업/거래목적 원문을 위험도로 분류하는 규칙표가 없어 항상 LOW로 넘긴다.
    // 분류표가 생기면 이 두 인자만 실제 분류 결과로 교체한다.
    CddAssessmentResult assessment =
        CddGradeCalculator.assess(
            RiskLevel.LOW, RiskLevel.LOW, request.identityVerificationMethod());

    Customer customer =
        new Customer(
            request.name(),
            request.loginId(),
            passwordEncoder.encode(request.password()),
            request.residentRegNo(),
            residentRegNoHash,
            request.birthDate(),
            request.phone(),
            request.address(),
            request.occupation(),
            request.identityVerificationMethod(),
            OffsetDateTime.now(),
            assessment.kycGrade(),
            assessment.amlRiskLevel(),
            request.transactionPurpose(),
            request.fundSource(),
            CustomerStatus.ACTIVE);
    Long customerId = customerRepository.save(customer).getCustomerId();

    historyRepository.save(
        new CustomerRiskAssessmentHistory(
            customerId,
            assessment.kycGrade(),
            assessment.amlRiskLevel(),
            request.transactionPurpose(),
            request.fundSource(),
            ASSESSED_BY_SYSTEM));

    return new CustomerRegisterResponse(
        String.valueOf(customerId),
        assessment.kycGrade(),
        assessment.amlRiskLevel(),
        CustomerStatus.ACTIVE,
        assessment.kycGrade() == KycGrade.EDD);
  }

  // ponytail: 소명 자료의 충분성을 판단하는 규칙이 없어 형식 검증(NotBlank) 통과만 확인하고
  // 항상 수리한다. 위험도 재평가 규칙표가 생기면 여기서 amlRiskLevel을 다시 계산한다.
  @Transactional
  public EddRegisterResponse registerEdd(Long customerId, EddRegisterRequest request) {
    Customer customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new CustomerException(ErrorCode.COMMON_004_NOT_FOUND));
    if (customer.getKycGrade() != KycGrade.EDD) {
      throw new CustomerException(ErrorCode.ACC_004_CUSTOMER_NOT_HIGH_RISK);
    }

    customer.recordEddConfirmation(request.transactionPurpose(), request.fundSource());

    OffsetDateTime completedAt = OffsetDateTime.now();
    historyRepository.save(
        new CustomerRiskAssessmentHistory(
            customerId,
            customer.getKycGrade(),
            customer.getAmlRiskLevel(),
            request.transactionPurpose(),
            request.fundSource(),
            "OPERATOR"));

    return new EddRegisterResponse(
        String.valueOf(customerId), customer.getAmlRiskLevel(), completedAt);
  }
}

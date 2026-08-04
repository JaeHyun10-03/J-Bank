package com.jbank.customer.service;

import com.jbank.common.crypto.ResidentRegNoHasher;
import com.jbank.common.event.CustomerGradeChangedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

  private static final String ASSESSED_BY_SYSTEM = "SYSTEM";

  private final CustomerRepository customerRepository;
  private final CustomerRiskAssessmentHistoryRepository historyRepository;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher eventPublisher;

  public CustomerService(
      CustomerRepository customerRepository,
      CustomerRiskAssessmentHistoryRepository historyRepository,
      PasswordEncoder passwordEncoder,
      ApplicationEventPublisher eventPublisher) {
    this.customerRepository = customerRepository;
    this.historyRepository = historyRepository;
    this.passwordEncoder = passwordEncoder;
    this.eventPublisher = eventPublisher;
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
    customer = customerRepository.save(customer);
    Long customerId = customer.getCustomerId();

    recordRiskAssessment(
        customer, null, null, request.transactionPurpose(), request.fundSource(),
        ASSESSED_BY_SYSTEM);

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

    KycGrade previousKycGrade = customer.getKycGrade();
    RiskLevel previousAmlRiskLevel = customer.getAmlRiskLevel();
    customer.recordEddConfirmation(request.transactionPurpose(), request.fundSource());

    OffsetDateTime completedAt = OffsetDateTime.now();
    recordRiskAssessment(
        customer,
        previousKycGrade,
        previousAmlRiskLevel,
        request.transactionPurpose(),
        request.fundSource(),
        "OPERATOR");

    return new EddRegisterResponse(
        String.valueOf(customerId), customer.getAmlRiskLevel(), completedAt);
  }

  // 등급을 바꾸는 모든 경로가 이 메서드 하나를 거치도록 모아, 이력 누락 없이 스냅샷을 남긴다.
  // previousKycGrade가 null이면 최초 등록(변경이 아님)이라 이벤트를 발행하지 않는다.
  private void recordRiskAssessment(
      Customer customer,
      KycGrade previousKycGrade,
      RiskLevel previousAmlRiskLevel,
      String transactionPurpose,
      String fundSource,
      String assessedBy) {
    historyRepository.save(
        new CustomerRiskAssessmentHistory(
            customer.getCustomerId(),
            customer.getKycGrade(),
            customer.getAmlRiskLevel(),
            transactionPurpose,
            fundSource,
            assessedBy));

    if (previousKycGrade != null
        && (previousKycGrade != customer.getKycGrade()
            || previousAmlRiskLevel != customer.getAmlRiskLevel())) {
      eventPublisher.publishEvent(
          new CustomerGradeChangedEvent(
              customer.getCustomerId(),
              previousKycGrade.name(),
              customer.getKycGrade().name(),
              previousAmlRiskLevel.name(),
              customer.getAmlRiskLevel().name(),
              assessedBy,
              OffsetDateTime.now()));
    }
  }
}

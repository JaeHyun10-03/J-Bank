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
import com.jbank.customer.repository.CustomerRepository;
import com.jbank.customer.repository.CustomerRiskAssessmentHistoryRepository;
import com.jbank.global.exception.ErrorCode;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

  private static final String ASSESSED_BY_SYSTEM = "SYSTEM";

  private final CustomerRepository customerRepository;
  private final CustomerRiskAssessmentHistoryRepository historyRepository;

  public CustomerService(
      CustomerRepository customerRepository,
      CustomerRiskAssessmentHistoryRepository historyRepository) {
    this.customerRepository = customerRepository;
    this.historyRepository = historyRepository;
  }

  @Transactional
  public CustomerRegisterResponse register(CustomerRegisterRequest request) {
    String residentRegNoHash = ResidentRegNoHasher.hash(request.residentRegNo());
    if (customerRepository.findByResidentRegNoHash(residentRegNoHash).isPresent()) {
      throw new CustomerException(ErrorCode.ACC_001_DUPLICATE_RESIDENT_REG_NO);
    }

    // ponytail: 직업/거래목적 원문을 위험도로 분류하는 규칙표가 없어 항상 LOW로 넘긴다.
    // 분류표가 생기면 이 두 인자만 실제 분류 결과로 교체한다.
    CddAssessmentResult assessment =
        CddGradeCalculator.assess(
            RiskLevel.LOW, RiskLevel.LOW, request.identityVerificationMethod());

    Customer customer =
        new Customer(
            request.name(),
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
}

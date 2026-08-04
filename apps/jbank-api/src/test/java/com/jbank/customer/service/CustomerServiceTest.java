package com.jbank.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.jbank.common.event.CustomerGradeChangedEvent;
import com.jbank.customer.domain.Customer;
import com.jbank.customer.domain.CustomerStatus;
import com.jbank.customer.domain.IdentityVerificationMethod;
import com.jbank.customer.domain.KycGrade;
import com.jbank.customer.domain.RiskLevel;
import com.jbank.customer.repository.CustomerRepository;
import com.jbank.customer.repository.CustomerRiskAssessmentHistoryRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

  @Mock private CustomerRepository customerRepository;
  @Mock private CustomerRiskAssessmentHistoryRepository historyRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private ApplicationEventPublisher eventPublisher;

  private CustomerService customerService;

  @BeforeEach
  void setUp() {
    customerService =
        new CustomerService(customerRepository, historyRepository, passwordEncoder, eventPublisher);
  }

  @Test
  void 이전_등급이_없으면_최초_등록으로_보고_이벤트를_발행하지_않는다() {
    Customer customer = newCustomer(KycGrade.GENERAL, RiskLevel.LOW);

    customerService.recordRiskAssessment(customer, null, null, "생활비", "근로소득", "SYSTEM");

    verify(eventPublisher, never()).publishEvent(any(CustomerGradeChangedEvent.class));
  }

  @Test
  void 이전_등급과_현재_등급이_같으면_이벤트를_발행하지_않는다() {
    Customer customer = newCustomer(KycGrade.GENERAL, RiskLevel.LOW);

    customerService.recordRiskAssessment(
        customer, KycGrade.GENERAL, RiskLevel.LOW, "생활비", "근로소득", "OPERATOR");

    verify(eventPublisher, never()).publishEvent(any(CustomerGradeChangedEvent.class));
  }

  @Test
  void 등급이_바뀌면_CustomerGradeChangedEvent를_발행한다() {
    Customer customer = newCustomer(KycGrade.EDD, RiskLevel.HIGH);

    customerService.recordRiskAssessment(
        customer, KycGrade.GENERAL, RiskLevel.LOW, "해외송금", "부동산 임대소득", "OPERATOR");

    ArgumentCaptor<CustomerGradeChangedEvent> captor =
        ArgumentCaptor.forClass(CustomerGradeChangedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    CustomerGradeChangedEvent event = captor.getValue();
    assertThat(event.customerId()).isEqualTo(customer.getCustomerId());
    assertThat(event.previousKycGrade()).isEqualTo("GENERAL");
    assertThat(event.newKycGrade()).isEqualTo("EDD");
    assertThat(event.previousAmlRiskLevel()).isEqualTo("LOW");
    assertThat(event.newAmlRiskLevel()).isEqualTo("HIGH");
    assertThat(event.assessedBy()).isEqualTo("OPERATOR");
  }

  private Customer newCustomer(KycGrade kycGrade, RiskLevel amlRiskLevel) {
    return new Customer(
        "정민성",
        "user-" + System.nanoTime(),
        "hash",
        "900101-1234567",
        "hash-" + System.nanoTime(),
        LocalDate.of(1990, 1, 1),
        "010-1234-5678",
        "서울특별시 강남구",
        "회사원",
        IdentityVerificationMethod.FACE_TO_FACE,
        OffsetDateTime.now(),
        kycGrade,
        amlRiskLevel,
        null,
        null,
        CustomerStatus.ACTIVE);
  }
}

package com.jbank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbank.common.crypto.HmacKeyHolder;
import com.jbank.common.crypto.PiiEncryptionKeyHolder;
import com.jbank.customer.domain.Customer;
import com.jbank.customer.domain.CustomerException;
import com.jbank.customer.domain.CustomerStatus;
import com.jbank.customer.domain.IdentityVerificationMethod;
import com.jbank.customer.domain.KycGrade;
import com.jbank.customer.domain.RiskLevel;
import com.jbank.customer.dto.CustomerRegisterRequest;
import com.jbank.customer.dto.CustomerRegisterResponse;
import com.jbank.customer.dto.EddRegisterRequest;
import com.jbank.customer.dto.EddRegisterResponse;
import com.jbank.customer.repository.CustomerRepository;
import com.jbank.customer.repository.CustomerRiskAssessmentHistoryRepository;
import com.jbank.customer.service.CustomerService;
import com.jbank.global.config.PasswordEncoderConfig;
import com.jbank.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  PiiEncryptionKeyHolder.class,
  HmacKeyHolder.class,
  PasswordEncoderConfig.class,
  CustomerService.class
})
class CustomerServiceIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("jbank.crypto.pii-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    registry.add("jbank.crypto.hash-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

  @Autowired private CustomerService customerService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerRiskAssessmentHistoryRepository historyRepository;

  private static CustomerRegisterRequest newRequest(String residentRegNo) {
    return newRequest(residentRegNo, "user-" + java.util.UUID.randomUUID());
  }

  private static CustomerRegisterRequest newRequest(String residentRegNo, String loginId) {
    return new CustomerRegisterRequest(
        "정민성",
        loginId,
        "password123!",
        residentRegNo,
        LocalDate.of(1990, 1, 1),
        "010-1234-5678",
        "서울특별시 강남구",
        "회사원",
        IdentityVerificationMethod.FACE_TO_FACE,
        null,
        null);
  }

  @Test
  void 고객을_등록하면_저위험_일반고객으로_판정되고_이력이_남는다() {
    // given
    CustomerRegisterRequest request = newRequest("900101-1234567");

    // when
    CustomerRegisterResponse response = customerService.register(request);

    // then
    assertThat(response.kycGrade()).isEqualTo(KycGrade.GENERAL);
    assertThat(response.amlRiskLevel()).isEqualTo(RiskLevel.LOW);
    assertThat(response.status()).isEqualTo(CustomerStatus.ACTIVE);
    assertThat(response.eddRequired()).isFalse();

    List<?> history = historyRepository.findByCustomerId(Long.valueOf(response.customerId()));
    assertThat(history).hasSize(1);
  }

  @Test
  void 이미_등록된_실명번호로_다시_등록하면_예외가_발생한다() {
    // given
    customerService.register(newRequest("900101-7654321"));

    // when & then
    assertThatThrownBy(() -> customerService.register(newRequest("900101-7654321")))
        .isInstanceOf(CustomerException.class)
        .satisfies(
            ex ->
                assertThat(((CustomerException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACC_001_DUPLICATE_RESIDENT_REG_NO));
  }

  @Test
  void 이미_사용중인_로그인ID로_등록하면_예외가_발생한다() {
    // given
    customerService.register(newRequest("900101-3333333", "dup-login-id"));

    // when & then
    assertThatThrownBy(() -> customerService.register(newRequest("900101-4444444", "dup-login-id")))
        .isInstanceOf(CustomerException.class)
        .satisfies(
            ex ->
                assertThat(((CustomerException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACC_011_DUPLICATE_LOGIN_ID));
  }

  @Test
  void 등록된_비밀번호는_평문이_아니라_bcrypt_해시로_저장된다() {
    // given
    CustomerRegisterResponse response = customerService.register(newRequest("900101-5555555"));

    // when
    Customer saved = customerRepository.findById(Long.valueOf(response.customerId())).orElseThrow();

    // then
    assertThat(saved.getPasswordHash()).isNotEqualTo("password123!");
    assertThat(saved.getPasswordHash()).startsWith("$2");
  }

  @Test
  void 고위험_고객이_EDD를_등록하면_이력이_추가되고_완료시각이_반환된다() {
    // given
    Long customerId = saveHighRiskCustomer();
    EddRegisterRequest request =
        new EddRegisterRequest("해외거주 가족 생활비 송금", "부동산 임대소득", "DOC-2026-004521");

    // when
    EddRegisterResponse response = customerService.registerEdd(customerId, request);

    // then
    assertThat(response.customerId()).isEqualTo(String.valueOf(customerId));
    assertThat(response.eddCompletedAt()).isNotNull();
    assertThat(historyRepository.findByCustomerId(customerId)).hasSize(1);
  }

  @Test
  void 고위험이_아닌_고객이_EDD를_등록하면_거절한다() {
    // given
    CustomerRegisterResponse registered = customerService.register(newRequest("900101-1111111"));
    Long customerId = Long.valueOf(registered.customerId());
    EddRegisterRequest request = new EddRegisterRequest("생활자금", "근로소득", "DOC-0001");

    // when & then
    assertThatThrownBy(() -> customerService.registerEdd(customerId, request))
        .isInstanceOf(CustomerException.class)
        .satisfies(
            ex ->
                assertThat(((CustomerException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACC_004_CUSTOMER_NOT_HIGH_RISK));
  }

  private Long saveHighRiskCustomer() {
    Customer customer =
        new Customer(
            "정민성",
            "user-" + System.nanoTime(),
            "hash-" + System.nanoTime(),
            "900101-2222222",
            "hash-" + System.nanoTime(),
            LocalDate.of(1990, 1, 1),
            "010-1234-5678",
            "서울특별시 강남구",
            "회사원",
            IdentityVerificationMethod.NON_FACE_TO_FACE,
            OffsetDateTime.now(),
            KycGrade.EDD,
            RiskLevel.HIGH,
            null,
            null,
            CustomerStatus.ACTIVE);
    return customerRepository.saveAndFlush(customer).getCustomerId();
  }
}

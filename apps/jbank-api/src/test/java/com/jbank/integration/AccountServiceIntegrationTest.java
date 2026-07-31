package com.jbank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbank.account.domain.AccountException;
import com.jbank.account.domain.AccountNumberGenerator;
import com.jbank.account.domain.AccountStatus;
import com.jbank.account.domain.AccountType;
import com.jbank.account.dto.AccountDetailResponse;
import com.jbank.account.dto.AccountOpenRequest;
import com.jbank.account.dto.AccountOpenResponse;
import com.jbank.account.dto.AccountStatusChangeRequest;
import com.jbank.account.dto.AccountStatusChangeResponse;
import com.jbank.account.service.AccountService;
import com.jbank.common.crypto.HmacKeyHolder;
import com.jbank.common.crypto.PiiEncryptionKeyHolder;
import com.jbank.customer.domain.Customer;
import com.jbank.customer.domain.CustomerStatus;
import com.jbank.customer.domain.IdentityVerificationMethod;
import com.jbank.customer.domain.KycGrade;
import com.jbank.customer.domain.RiskLevel;
import com.jbank.customer.repository.CustomerRepository;
import com.jbank.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
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
  AccountNumberGenerator.class,
  AccountService.class
})
class AccountServiceIntegrationTest {

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

  @Autowired private CustomerRepository customerRepository;
  @Autowired private AccountService accountService;

  @Test
  void 정상_고객이면_잔액0원_계좌를_개설한다() {
    // given
    Long customerId = saveCustomer(KycGrade.GENERAL, RiskLevel.LOW, CustomerStatus.ACTIVE);
    AccountOpenRequest request =
        new AccountOpenRequest(String.valueOf(customerId), AccountType.CHECKING, BigDecimal.ZERO);

    // when
    AccountOpenResponse response = accountService.open(request);

    // then
    assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(response.accountNumber()).startsWith("110-");
  }

  @Test
  void 초기입금이_0원이_아니면_거절한다() {
    // given
    Long customerId = saveCustomer(KycGrade.GENERAL, RiskLevel.LOW, CustomerStatus.ACTIVE);
    AccountOpenRequest request =
        new AccountOpenRequest(
            String.valueOf(customerId), AccountType.CHECKING, new BigDecimal("1000"));

    // when & then
    assertThatThrownBy(() -> accountService.open(request))
        .isInstanceOf(AccountException.class)
        .satisfies(
            ex ->
                assertThat(((AccountException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.COMMON_001_VALIDATION_FAILED));
  }

  @Test
  void EDD_대상_고객은_추가확인_전에는_계좌를_개설할_수_없다() {
    // given
    Long customerId = saveCustomer(KycGrade.EDD, RiskLevel.HIGH, CustomerStatus.ACTIVE);
    AccountOpenRequest request =
        new AccountOpenRequest(String.valueOf(customerId), AccountType.CHECKING, BigDecimal.ZERO);

    // when & then
    assertThatThrownBy(() -> accountService.open(request))
        .isInstanceOf(AccountException.class)
        .satisfies(
            ex ->
                assertThat(((AccountException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACC_005_CDD_NOT_COMPLETED));
  }

  @Test
  void 해지된_고객은_계좌를_개설할_수_없다() {
    // given
    Long customerId = saveCustomer(KycGrade.GENERAL, RiskLevel.LOW, CustomerStatus.CLOSED);
    AccountOpenRequest request =
        new AccountOpenRequest(String.valueOf(customerId), AccountType.CHECKING, BigDecimal.ZERO);

    // when & then
    assertThatThrownBy(() -> accountService.open(request))
        .isInstanceOf(AccountException.class)
        .satisfies(
            ex ->
                assertThat(((AccountException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACC_006_CUSTOMER_STATUS_INVALID));
  }

  @Test
  void 계좌_소유자가_조회하면_상세정보를_반환한다() {
    // given
    Long customerId = saveCustomer(KycGrade.GENERAL, RiskLevel.LOW, CustomerStatus.ACTIVE);
    Long accountId =
        Long.valueOf(
            accountService
                .open(
                    new AccountOpenRequest(
                        String.valueOf(customerId), AccountType.CHECKING, BigDecimal.ZERO))
                .accountId());

    // when
    AccountDetailResponse detail = accountService.getDetail(accountId, customerId);

    // then
    assertThat(detail.accountId()).isEqualTo(String.valueOf(accountId));
    assertThat(detail.customerId()).isEqualTo(String.valueOf(customerId));
    assertThat(detail.status()).isEqualTo(AccountStatus.ACTIVE);
  }

  @Test
  void 소유자가_아니면_조회를_거절한다() {
    // given
    Long ownerId = saveCustomer(KycGrade.GENERAL, RiskLevel.LOW, CustomerStatus.ACTIVE);
    Long strangerId = saveCustomer(KycGrade.GENERAL, RiskLevel.LOW, CustomerStatus.ACTIVE);
    Long accountId =
        Long.valueOf(
            accountService
                .open(
                    new AccountOpenRequest(
                        String.valueOf(ownerId), AccountType.CHECKING, BigDecimal.ZERO))
                .accountId());

    // when & then
    assertThatThrownBy(() -> accountService.getDetail(accountId, strangerId))
        .isInstanceOf(AccountException.class)
        .satisfies(
            ex ->
                assertThat(((AccountException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.COMMON_003_FORBIDDEN));
  }

  @Test
  void 존재하지_않는_계좌를_조회하면_404에_해당하는_예외가_발생한다() {
    // when & then
    assertThatThrownBy(() -> accountService.getDetail(999_999L, 1L))
        .isInstanceOf(AccountException.class)
        .satisfies(
            ex ->
                assertThat(((AccountException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.COMMON_004_NOT_FOUND));
  }

  @Test
  void 정지로_전이하면_상태가_바뀐다() {
    // given
    Long customerId = saveCustomer(KycGrade.GENERAL, RiskLevel.LOW, CustomerStatus.ACTIVE);
    Long accountId =
        Long.valueOf(
            accountService
                .open(
                    new AccountOpenRequest(
                        String.valueOf(customerId), AccountType.CHECKING, BigDecimal.ZERO))
                .accountId());

    // when
    AccountStatusChangeResponse response =
        accountService.changeStatus(
            accountId, new AccountStatusChangeRequest(AccountStatus.SUSPENDED, "이상거래 의심"));

    // then
    assertThat(response.previousStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(response.status()).isEqualTo(AccountStatus.SUSPENDED);
    assertThat(accountService.getDetail(accountId, customerId).status())
        .isEqualTo(AccountStatus.SUSPENDED);
  }

  @Test
  void 허용되지_않는_전이는_거절한다() {
    // given
    Long customerId = saveCustomer(KycGrade.GENERAL, RiskLevel.LOW, CustomerStatus.ACTIVE);
    Long accountId =
        Long.valueOf(
            accountService
                .open(
                    new AccountOpenRequest(
                        String.valueOf(customerId), AccountType.CHECKING, BigDecimal.ZERO))
                .accountId());

    // when & then
    assertThatThrownBy(
            () ->
                accountService.changeStatus(
                    accountId, new AccountStatusChangeRequest(AccountStatus.CLOSED, "임의 해지 시도")))
        .isInstanceOf(AccountException.class)
        .satisfies(
            ex ->
                assertThat(((AccountException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACC_007_INVALID_STATUS_TRANSITION));
  }

  private Long saveCustomer(KycGrade kycGrade, RiskLevel riskLevel, CustomerStatus status) {
    Customer customer =
        new Customer(
            "정민성",
            "900101-1234567",
            "hash-" + System.nanoTime(),
            LocalDate.of(1990, 1, 1),
            "010-1234-5678",
            "서울특별시 강남구",
            "회사원",
            IdentityVerificationMethod.FACE_TO_FACE,
            OffsetDateTime.now(),
            kycGrade,
            riskLevel,
            null,
            null,
            status);
    return customerRepository.saveAndFlush(customer).getCustomerId();
  }
}

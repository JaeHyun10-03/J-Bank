package com.jbank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountStatus;
import com.jbank.account.domain.AccountType;
import com.jbank.account.repository.AccountRepository;
import com.jbank.common.crypto.HmacKeyHolder;
import com.jbank.common.crypto.PiiEncryptionKeyHolder;
import com.jbank.customer.domain.Customer;
import com.jbank.customer.domain.CustomerStatus;
import com.jbank.customer.domain.IdentityVerificationMethod;
import com.jbank.customer.domain.KycGrade;
import com.jbank.customer.domain.RiskLevel;
import com.jbank.customer.repository.CustomerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PiiEncryptionKeyHolder.class, HmacKeyHolder.class})
class AccountRepositoryIntegrationTest {

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
  @Autowired private AccountRepository accountRepository;

  @Test
  void 저장한_계좌를_다시_조회하면_값이_그대로_복원된다() {
    // given
    Long customerId = savedCustomerId();
    Account account =
        new Account(
            "110-000001-4",
            customerId,
            AccountType.CHECKING,
            AccountStatus.ACTIVE,
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            OffsetDateTime.now());

    // when
    Long savedId = accountRepository.saveAndFlush(account).getAccountId();
    Account found = accountRepository.findById(savedId).orElseThrow();

    // then
    assertThat(found.getAccountNumber()).isEqualTo("110-000001-4");
    assertThat(found.getCustomerId()).isEqualTo(customerId);
    assertThat(found.getCurrentBalanceCache()).isEqualByComparingTo("0.00");
    assertThat(found.getHoldAmount()).isEqualByComparingTo("0.00");
  }

  @Test
  void 같은_계좌번호로_두_번_저장하면_유니크_제약을_위반한다() {
    // given
    Long customerId = savedCustomerId();
    accountRepository.saveAndFlush(
        new Account(
            "110-000002-2",
            customerId,
            AccountType.CHECKING,
            AccountStatus.ACTIVE,
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            OffsetDateTime.now()));

    // when & then
    assertThatThrownBy(
            () ->
                accountRepository.saveAndFlush(
                    new Account(
                        "110-000002-2",
                        customerId,
                        AccountType.CHECKING,
                        AccountStatus.ACTIVE,
                        new BigDecimal("0.00"),
                        new BigDecimal("0.00"),
                        OffsetDateTime.now())))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Long savedCustomerId() {
    Customer customer =
        new Customer(
            "정민성",
            "user-" + System.nanoTime(),
            "hash-" + System.nanoTime(),
            "900101-1234567",
            "hash-" + System.nanoTime(),
            LocalDate.of(1990, 1, 1),
            "010-1234-5678",
            "서울특별시 강남구",
            "회사원",
            IdentityVerificationMethod.NON_FACE_TO_FACE,
            OffsetDateTime.now(),
            KycGrade.GENERAL,
            RiskLevel.LOW,
            null,
            null,
            CustomerStatus.ACTIVE);
    return customerRepository.saveAndFlush(customer).getCustomerId();
  }
}

package com.jbank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountException;
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
import com.jbank.global.exception.ErrorCode;
import com.jbank.ledger.repository.LedgerEntryRepository;
import com.jbank.transfer.dto.AccountTransactionResponse;
import com.jbank.transfer.repository.TransactionRepository;
import com.jbank.transfer.service.DepositService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
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
@Import({PiiEncryptionKeyHolder.class, HmacKeyHolder.class, DepositService.class})
class DepositServiceIntegrationTest {

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

  @Autowired private AccountRepository accountRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private LedgerEntryRepository ledgerEntryRepository;
  @Autowired private DepositService depositService;

  @Test
  void 정상_입금은_잔액을_늘리고_원장_한_건을_남긴다() {
    Account account = saveAccount(new BigDecimal("1000.00"), AccountStatus.ACTIVE);

    AccountTransactionResponse response =
        depositService.deposit(
            account.getAccountId(), new BigDecimal("500.00"), UUID.randomUUID().toString());

    assertThat(response.balanceAfter()).isEqualByComparingTo("1500.00");
    Account updated = accountRepository.findById(account.getAccountId()).orElseThrow();
    assertThat(updated.getCurrentBalanceCache()).isEqualByComparingTo("1500.00");
    assertThat(ledgerEntryRepository.findByAccountId(account.getAccountId())).hasSize(1);
  }

  @Test
  void 동일한_멱등성_키로_재요청하면_한_번만_반영된다() {
    Account account = saveAccount(new BigDecimal("1000.00"), AccountStatus.ACTIVE);
    String idempotencyKey = UUID.randomUUID().toString();

    AccountTransactionResponse first =
        depositService.deposit(account.getAccountId(), new BigDecimal("500.00"), idempotencyKey);
    AccountTransactionResponse second =
        depositService.deposit(account.getAccountId(), new BigDecimal("500.00"), idempotencyKey);

    assertThat(second.transactionId()).isEqualTo(first.transactionId());
    Account updated = accountRepository.findById(account.getAccountId()).orElseThrow();
    assertThat(updated.getCurrentBalanceCache()).isEqualByComparingTo("1500.00");
  }

  @Test
  void 정지된_계좌는_입금을_거절한다() {
    Account account = saveAccount(new BigDecimal("1000.00"), AccountStatus.SUSPENDED);

    assertThatThrownBy(
            () ->
                depositService.deposit(
                    account.getAccountId(), new BigDecimal("500.00"), UUID.randomUUID().toString()))
        .isInstanceOf(AccountException.class)
        .satisfies(
            ex ->
                assertThat(((AccountException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACC_009_ACCOUNT_STATUS_INVALID));
  }

  private Account saveAccount(BigDecimal balance, AccountStatus status) {
    Long customerId = saveCustomer();
    Account account =
        new Account(
            "110-" + UUID.randomUUID().toString().substring(0, 9),
            customerId,
            AccountType.CHECKING,
            status,
            balance,
            BigDecimal.ZERO,
            OffsetDateTime.now());
    return accountRepository.saveAndFlush(account);
  }

  private Long saveCustomer() {
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
            KycGrade.GENERAL,
            RiskLevel.LOW,
            null,
            null,
            CustomerStatus.ACTIVE);
    return customerRepository.saveAndFlush(customer).getCustomerId();
  }
}

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
import com.jbank.support.audit.AuditLogListener;
import com.jbank.support.audit.repository.AuditLogRepository;
import com.jbank.transfer.domain.TransactionException;
import com.jbank.transfer.domain.TransactionStatus;
import com.jbank.transfer.dto.TransferResponse;
import com.jbank.transfer.repository.TransactionRepository;
import com.jbank.transfer.service.IdempotencyRecovery;
import com.jbank.transfer.service.TransferService;
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
@Import({
  PiiEncryptionKeyHolder.class,
  HmacKeyHolder.class,
  IdempotencyRecovery.class,
  TransferService.class,
  AuditLogListener.class
})
class TransferServiceIntegrationTest {

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
  @Autowired private TransferService transferService;
  @Autowired private AuditLogRepository auditLogRepository;

  @Test
  void 정상_이체는_잔액을_옮기고_원장_두_건을_남긴다() {
    Account from = saveAccount(new BigDecimal("100000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE);
    Account to = saveAccount(new BigDecimal("5000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE);

    TransferResponse response =
        transferService.transfer(
            from.getAccountNumber(),
            to.getAccountNumber(),
            new BigDecimal("30000.00"),
            UUID.randomUUID().toString(),
            "생활비",
            from.getCustomerId());

    assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
    assertThat(response.fromAccountBalanceAfter()).isEqualByComparingTo("70000.00");
    Account updatedFrom = accountRepository.findById(from.getAccountId()).orElseThrow();
    Account updatedTo = accountRepository.findById(to.getAccountId()).orElseThrow();
    assertThat(updatedFrom.getCurrentBalanceCache()).isEqualByComparingTo("70000.00");
    assertThat(updatedTo.getCurrentBalanceCache()).isEqualByComparingTo("35000.00");
    assertThat(ledgerEntryRepository.findByAccountId(from.getAccountId())).hasSize(1);
    assertThat(ledgerEntryRepository.findByAccountId(to.getAccountId())).hasSize(1);
    assertThat(auditLogRepository.findAll())
        .anySatisfy(
            log -> {
              assertThat(log.getEventType()).isEqualTo("TRANSFER_COMPLETED");
              assertThat(log.getTargetId()).isEqualTo(String.valueOf(response.transactionId()));
            });
  }

  @Test
  void 동일한_멱등성_키로_재요청하면_원래_결과를_그대로_반환한다() {
    Account from = saveAccount(new BigDecimal("100000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE);
    Account to = saveAccount(new BigDecimal("5000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE);
    String idempotencyKey = UUID.randomUUID().toString();

    TransferResponse first =
        transferService.transfer(
            from.getAccountNumber(),
            to.getAccountNumber(),
            new BigDecimal("30000.00"),
            idempotencyKey,
            null,
            from.getCustomerId());
    TransferResponse second =
        transferService.transfer(
            from.getAccountNumber(),
            to.getAccountNumber(),
            new BigDecimal("30000.00"),
            idempotencyKey,
            null,
            from.getCustomerId());

    assertThat(second.transactionId()).isEqualTo(first.transactionId());
    Account updatedFrom = accountRepository.findById(from.getAccountId()).orElseThrow();
    assertThat(updatedFrom.getCurrentBalanceCache()).isEqualByComparingTo("70000.00");
    assertThat(ledgerEntryRepository.findByAccountId(from.getAccountId())).hasSize(1);
  }

  @Test
  void 소유자가_아니면_이체를_거절한다() {
    Account from = saveAccount(new BigDecimal("100000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE);
    Account to = saveAccount(new BigDecimal("0.00"), BigDecimal.ZERO, AccountStatus.ACTIVE);
    Long strangerId = saveCustomer();

    assertThatThrownBy(
            () ->
                transferService.transfer(
                    from.getAccountNumber(),
                    to.getAccountNumber(),
                    BigDecimal.TEN,
                    UUID.randomUUID().toString(),
                    null,
                    strangerId))
        .isInstanceOf(AccountException.class)
        .satisfies(
            ex ->
                assertThat(((AccountException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.COMMON_003_FORBIDDEN));
  }

  @Test
  void 출금계좌와_입금계좌가_같으면_거절한다() {
    Account account =
        saveAccount(new BigDecimal("10000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE);

    assertThatThrownBy(
            () ->
                transferService.transfer(
                    account.getAccountNumber(),
                    account.getAccountNumber(),
                    BigDecimal.TEN,
                    UUID.randomUUID().toString(),
                    null,
                    account.getCustomerId()))
        .isInstanceOf(TransactionException.class)
        .satisfies(
            ex ->
                assertThat(((TransactionException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.TXN_002_SAME_ACCOUNT_TRANSFER));
  }

  @Test
  void 출금가능금액을_초과하면_거절한다() {
    Account from = saveAccount(new BigDecimal("1000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE);
    Account to = saveAccount(BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.ACTIVE);

    assertThatThrownBy(
            () ->
                transferService.transfer(
                    from.getAccountNumber(),
                    to.getAccountNumber(),
                    new BigDecimal("2000.00"),
                    UUID.randomUUID().toString(),
                    null,
                    from.getCustomerId()))
        .isInstanceOf(TransactionException.class)
        .satisfies(
            ex ->
                assertThat(((TransactionException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.TXN_001_INSUFFICIENT_BALANCE));
  }

  @Test
  void 지급정지_금액은_출금가능금액에서_제외된다() {
    Account from =
        saveAccount(new BigDecimal("10000.00"), new BigDecimal("9500.00"), AccountStatus.ACTIVE);
    Account to = saveAccount(BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.ACTIVE);

    assertThatThrownBy(
            () ->
                transferService.transfer(
                    from.getAccountNumber(),
                    to.getAccountNumber(),
                    new BigDecimal("1000.00"),
                    UUID.randomUUID().toString(),
                    null,
                    from.getCustomerId()))
        .isInstanceOf(TransactionException.class)
        .satisfies(
            ex ->
                assertThat(((TransactionException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.TXN_001_INSUFFICIENT_BALANCE));
  }

  @Test
  void 정지된_출금계좌는_거절한다() {
    Account from =
        saveAccount(new BigDecimal("10000.00"), BigDecimal.ZERO, AccountStatus.SUSPENDED);
    Account to = saveAccount(BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.ACTIVE);

    assertThatThrownBy(
            () ->
                transferService.transfer(
                    from.getAccountNumber(),
                    to.getAccountNumber(),
                    BigDecimal.TEN,
                    UUID.randomUUID().toString(),
                    null,
                    from.getCustomerId()))
        .isInstanceOf(AccountException.class)
        .satisfies(
            ex ->
                assertThat(((AccountException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACC_009_ACCOUNT_STATUS_INVALID));
  }

  @Test
  void 정지된_입금계좌는_거절한다() {
    Account from = saveAccount(new BigDecimal("10000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE);
    Account to = saveAccount(BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.SUSPENDED);

    assertThatThrownBy(
            () ->
                transferService.transfer(
                    from.getAccountNumber(),
                    to.getAccountNumber(),
                    BigDecimal.TEN,
                    UUID.randomUUID().toString(),
                    null,
                    from.getCustomerId()))
        .isInstanceOf(TransactionException.class)
        .satisfies(
            ex ->
                assertThat(((TransactionException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.TXN_004_COUNTERPARTY_ACCOUNT_INVALID));
  }

  @Test
  void 존재하지_않는_입금계좌는_거절한다() {
    Account from = saveAccount(new BigDecimal("10000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE);

    assertThatThrownBy(
            () ->
                transferService.transfer(
                    from.getAccountNumber(),
                    "999-999-999999",
                    BigDecimal.TEN,
                    UUID.randomUUID().toString(),
                    null,
                    from.getCustomerId()))
        .isInstanceOf(TransactionException.class)
        .satisfies(
            ex ->
                assertThat(((TransactionException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.TXN_003_COUNTERPARTY_ACCOUNT_NOT_FOUND));
  }

  private Account saveAccount(BigDecimal balance, BigDecimal holdAmount, AccountStatus status) {
    Long customerId = saveCustomer();
    Account account =
        new Account(
            "110-" + UUID.randomUUID().toString().substring(0, 9),
            customerId,
            AccountType.CHECKING,
            status,
            balance,
            holdAmount,
            OffsetDateTime.now());
    return accountRepository.saveAndFlush(account);
  }

  private Long saveCustomer() {
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

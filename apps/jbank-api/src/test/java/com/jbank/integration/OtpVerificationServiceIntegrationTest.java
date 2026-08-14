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
import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionException;
import com.jbank.transfer.domain.TransactionStatus;
import com.jbank.transfer.dto.TransferResponse;
import com.jbank.transfer.repository.TransactionRepository;
import com.jbank.transfer.service.IdempotencyRecovery;
import com.jbank.transfer.service.OtpService;
import com.jbank.transfer.service.OtpVerificationService;
import com.jbank.transfer.service.PendingOtpCancellationService;
import com.jbank.transfer.service.TransferService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
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
  OtpService.class,
  TransferService.class,
  PendingOtpCancellationService.class,
  OtpVerificationService.class,
  OtpVerificationServiceIntegrationTest.RedisTestConfig.class
})
class OtpVerificationServiceIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @TestConfiguration
  static class RedisTestConfig {
    @Bean
    RedissonClient redissonClient() {
      Config config = new Config();
      config
          .useSingleServer()
          .setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
      return Redisson.create(config);
    }
  }

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
  @Autowired private OtpService otpService;
  @Autowired private OtpVerificationService otpVerificationService;
  @Autowired private RedissonClient redissonClient;

  @Test
  void 올바른_OTP로_검증하면_지급정지가_해제되고_이체가_완료된다() {
    Account from =
        saveAccount(new BigDecimal("20000000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE);
    Account to = saveAccount(BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.ACTIVE);
    Long transactionId = openPendingOtp(from, to, "15000000.00");
    String code = otpService.issue(transactionId);

    TransferResponse response =
        otpVerificationService.verify(transactionId, code, from.getCustomerId());

    assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
    Account updatedFrom = accountRepository.findById(from.getAccountId()).orElseThrow();
    Account updatedTo = accountRepository.findById(to.getAccountId()).orElseThrow();
    assertThat(updatedFrom.getHoldAmount()).isEqualByComparingTo("0.00");
    assertThat(updatedFrom.getCurrentBalanceCache()).isEqualByComparingTo("5000000.00");
    assertThat(updatedTo.getCurrentBalanceCache()).isEqualByComparingTo("15000000.00");
    assertThat(ledgerEntryRepository.findByAccountId(from.getAccountId())).hasSize(1);
    assertThat(ledgerEntryRepository.findByAccountId(to.getAccountId())).hasSize(1);
  }

  @Test
  void OTP가_틀리면_거래는_그대로_대기_상태로_남는다() {
    Account from =
        saveAccount(new BigDecimal("20000000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE);
    Account to = saveAccount(BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.ACTIVE);
    Long transactionId = openPendingOtp(from, to, "15000000.00");
    otpService.issue(transactionId);

    assertThatThrownBy(
            () -> otpVerificationService.verify(transactionId, "000000", from.getCustomerId()))
        .isInstanceOf(TransactionException.class)
        .satisfies(
            ex ->
                assertThat(((TransactionException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_004_OTP_MISMATCH));

    Transaction unchanged = transactionRepository.findById(transactionId).orElseThrow();
    Account unchangedFrom = accountRepository.findById(from.getAccountId()).orElseThrow();
    assertThat(unchanged.getStatus()).isEqualTo(TransactionStatus.PENDING_OTP);
    assertThat(unchangedFrom.getHoldAmount()).isEqualByComparingTo("15000000.00");
  }

  @Test
  void 실패_횟수가_한도를_넘으면_거래가_취소되고_지급정지가_풀린다() {
    Account from =
        saveAccount(new BigDecimal("20000000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE);
    Account to = saveAccount(BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.ACTIVE);
    Long transactionId = openPendingOtp(from, to, "15000000.00");
    otpService.issue(transactionId);
    for (int i = 0; i < 4; i++) {
      otpService.verify(transactionId, "wrong-" + i);
    }

    assertThatThrownBy(
            () -> otpVerificationService.verify(transactionId, "wrong-final", from.getCustomerId()))
        .isInstanceOf(TransactionException.class)
        .satisfies(
            ex ->
                assertThat(((TransactionException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_004_OTP_MISMATCH));

    Transaction cancelled = transactionRepository.findById(transactionId).orElseThrow();
    Account releasedFrom = accountRepository.findById(from.getAccountId()).orElseThrow();
    assertThat(cancelled.getStatus()).isEqualTo(TransactionStatus.CANCELLED);
    assertThat(releasedFrom.getHoldAmount()).isEqualByComparingTo("0.00");
  }

  @Test
  void 만료된_OTP를_검증하면_거래가_취소되고_지급정지가_풀린다() {
    Account from =
        saveAccount(new BigDecimal("20000000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE);
    Account to = saveAccount(BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.ACTIVE);
    Long transactionId = openPendingOtp(from, to, "15000000.00");
    // transfer()가 이미 OTP를 발급했으므로, 실제 3분 TTL 만료를 흉내 내려면 저장된 코드를 직접 지운다.
    RBucket<String> otpBucket = redissonClient.getBucket("transfer:otp:" + transactionId);
    otpBucket.delete();

    assertThatThrownBy(
            () -> otpVerificationService.verify(transactionId, "000000", from.getCustomerId()))
        .isInstanceOf(TransactionException.class)
        .satisfies(
            ex ->
                assertThat(((TransactionException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_005_OTP_EXPIRED));

    Transaction cancelled = transactionRepository.findById(transactionId).orElseThrow();
    Account releasedFrom = accountRepository.findById(from.getAccountId()).orElseThrow();
    assertThat(cancelled.getStatus()).isEqualTo(TransactionStatus.CANCELLED);
    assertThat(releasedFrom.getHoldAmount()).isEqualByComparingTo("0.00");
  }

  @Test
  void 이미_완료된_거래를_검증하면_거절한다() {
    Account from = saveAccount(new BigDecimal("100000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE);
    Account to = saveAccount(BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.ACTIVE);
    TransferResponse completed =
        transferService.transfer(
            from.getAccountNumber(),
            to.getAccountNumber(),
            new BigDecimal("1000.00"),
            UUID.randomUUID().toString(),
            null,
            from.getCustomerId());
    Long transactionId = Long.valueOf(completed.transactionId());

    assertThatThrownBy(
            () -> otpVerificationService.verify(transactionId, "000000", from.getCustomerId()))
        .isInstanceOf(TransactionException.class)
        .satisfies(
            ex ->
                assertThat(((TransactionException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.TXN_005_TRANSACTION_NOT_PENDING));
  }

  @Test
  void 소유자가_아니면_거절한다() {
    Account from =
        saveAccount(new BigDecimal("20000000.00"), BigDecimal.ZERO, AccountStatus.ACTIVE);
    Account to = saveAccount(BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.ACTIVE);
    Long transactionId = openPendingOtp(from, to, "15000000.00");
    String code = otpService.issue(transactionId);
    Long strangerId = saveCustomer();

    assertThatThrownBy(() -> otpVerificationService.verify(transactionId, code, strangerId))
        .isInstanceOf(AccountException.class)
        .satisfies(
            ex ->
                assertThat(((AccountException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.COMMON_003_FORBIDDEN));
  }

  private Long openPendingOtp(Account from, Account to, String amount) {
    TransferResponse response =
        transferService.transfer(
            from.getAccountNumber(),
            to.getAccountNumber(),
            new BigDecimal(amount),
            UUID.randomUUID().toString(),
            null,
            from.getCustomerId());
    return Long.valueOf(response.transactionId());
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

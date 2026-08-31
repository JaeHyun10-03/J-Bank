package com.jbank.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountStatus;
import com.jbank.account.domain.AccountType;
import com.jbank.account.repository.AccountRepository;
import com.jbank.customer.domain.Customer;
import com.jbank.customer.domain.CustomerStatus;
import com.jbank.customer.domain.IdentityVerificationMethod;
import com.jbank.customer.domain.KycGrade;
import com.jbank.customer.domain.RiskLevel;
import com.jbank.customer.repository.CustomerRepository;
import com.jbank.support.fds.domain.FdsRuleType;
import com.jbank.support.fds.repository.SuspiciousTransactionRepository;
import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionType;
import com.jbank.transfer.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/** 이상거래 탐지 배치 잡(구현계획 W7, FR-SUP-003)이 세 룰을 정확히 판별하고 재실행에 안전한지 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@SpringBatchTest
class FdsDetectionJobIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  static {
    POSTGRES.start();
    REDIS.start();
  }

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("jbank.crypto.pii-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    registry.add("jbank.crypto.hash-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    registry.add("jbank.jwt.secret", () -> "test-secret-key-at-least-32-bytes-long-for-hs256");
    registry.add("jbank.batch.fds.single-transaction-threshold", () -> "1000");
    registry.add("jbank.batch.fds.rapid-repeated.window-minutes", () -> "5");
    registry.add("jbank.batch.fds.rapid-repeated.min-count", () -> "3");
    registry.add("jbank.batch.fds.late-night.threshold", () -> "500");
  }

  @Autowired private JobLauncherTestUtils jobLauncherTestUtils;

  @Autowired
  @Qualifier("fdsDetectionJob")
  private Job fdsDetectionJob;

  @Autowired private CustomerRepository customerRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private SuspiciousTransactionRepository suspiciousTransactionRepository;

  @BeforeEach
  void setJob() {
    jobLauncherTestUtils.setJob(fdsDetectionJob);
  }

  @Test
  void 단일_거래가_임계금액을_넘으면_적재한다() throws Exception {
    Long customerId = saveCustomer();
    Account account = saveAccount(customerId);
    Transaction transaction =
        saveCompletedTransfer(account, new BigDecimal("1500"), "2026-08-20T14:00:00+09:00");

    JobExecution execution =
        jobLauncherTestUtils.launchJob(
            new JobParametersBuilder().addString("runDate", "2026-08-20").toJobParameters());

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(
            suspiciousTransactionRepository.existsByTransactionIdAndRuleType(
                transaction.getTransactionId(), FdsRuleType.SINGLE_TRANSACTION_THRESHOLD_EXCEEDED))
        .isTrue();
  }

  @Test
  void 임계금액_미달_주간_거래는_적재하지_않는다() throws Exception {
    Long customerId = saveCustomer();
    Account account = saveAccount(customerId);
    Transaction transaction =
        saveCompletedTransfer(account, new BigDecimal("100"), "2026-08-21T14:00:00+09:00");

    jobLauncherTestUtils.launchJob(
        new JobParametersBuilder().addString("runDate", "2026-08-21").toJobParameters());

    boolean matchedAnyRule =
        suspiciousTransactionRepository.findAll().stream()
            .anyMatch(row -> row.getTransactionId().equals(transaction.getTransactionId()));
    assertThat(matchedAnyRule).isFalse();
  }

  @Test
  void 짧은_시간_내_반복_이체는_적재한다() throws Exception {
    Long customerId = saveCustomer();
    Account account = saveAccount(customerId);
    saveCompletedTransfer(account, new BigDecimal("100"), "2026-08-22T10:00:00+09:00");
    saveCompletedTransfer(account, new BigDecimal("100"), "2026-08-22T10:01:00+09:00");
    Transaction third =
        saveCompletedTransfer(account, new BigDecimal("100"), "2026-08-22T10:02:00+09:00");

    jobLauncherTestUtils.launchJob(
        new JobParametersBuilder().addString("runDate", "2026-08-22").toJobParameters());

    assertThat(
            suspiciousTransactionRepository.existsByTransactionIdAndRuleType(
                third.getTransactionId(), FdsRuleType.RAPID_REPEATED_TRANSFER))
        .isTrue();
  }

  @Test
  void 심야_시간대_고액_이체는_적재한다() throws Exception {
    Long customerId = saveCustomer();
    Account account = saveAccount(customerId);
    Transaction transaction =
        saveCompletedTransfer(account, new BigDecimal("600"), "2026-08-23T23:30:00+09:00");

    jobLauncherTestUtils.launchJob(
        new JobParametersBuilder().addString("runDate", "2026-08-23").toJobParameters());

    assertThat(
            suspiciousTransactionRepository.existsByTransactionIdAndRuleType(
                transaction.getTransactionId(), FdsRuleType.LATE_NIGHT_HIGH_VALUE_TRANSFER))
        .isTrue();
  }

  @Test
  void 같은_기준일로_재실행해도_중복_적재하지_않는다() throws Exception {
    Long customerId = saveCustomer();
    Account account = saveAccount(customerId);
    Transaction transaction =
        saveCompletedTransfer(account, new BigDecimal("1500"), "2026-08-24T14:00:00+09:00");

    jobLauncherTestUtils.launchJob(
        new JobParametersBuilder().addString("runDate", "2026-08-24").toJobParameters());
    jobLauncherTestUtils
        .getJobLauncher()
        .run(
            jobLauncherTestUtils.getJob(),
            jobLauncherTestUtils
                .getUniqueJobParametersBuilder()
                .addString("runDate", "2026-08-24")
                .toJobParameters());

    long matches =
        suspiciousTransactionRepository.findAll().stream()
            .filter(row -> row.getTransactionId().equals(transaction.getTransactionId()))
            .count();
    assertThat(matches).isEqualTo(1);
  }

  private Transaction saveCompletedTransfer(Account from, BigDecimal amount, String processedAt) {
    Transaction transaction =
        new Transaction(
            TransactionType.TRANSFER,
            from.getAccountId(),
            null,
            amount,
            "FDS-" + System.nanoTime(),
            "FDS 테스트");
    transaction = transactionRepository.save(transaction);
    transaction.complete(OffsetDateTime.parse(processedAt));
    return transactionRepository.save(transaction);
  }

  private Long saveCustomer() {
    Customer customer =
        new Customer(
            "정민성",
            "fds-user-" + System.nanoTime(),
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

  private Account saveAccount(Long customerId) {
    Account account =
        new Account(
            "110-" + System.nanoTime(),
            customerId,
            AccountType.CHECKING,
            AccountStatus.ACTIVE,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            OffsetDateTime.now());
    return accountRepository.saveAndFlush(account);
  }
}

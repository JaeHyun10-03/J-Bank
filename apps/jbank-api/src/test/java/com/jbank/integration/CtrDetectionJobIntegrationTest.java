package com.jbank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.jbank.support.ctr.domain.CtrReportStatus;
import com.jbank.support.ctr.repository.CtrReportQueueRepository;
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
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/** 고액현금거래 판별 배치 잡(구현계획 W5)이 기준 금액 초과 여부를 정확히 판별하고 재실행에 안전한지 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@SpringBatchTest
class CtrDetectionJobIntegrationTest {

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
    registry.add("jbank.batch.ctr.threshold-amount", () -> "1000");
  }

  @Autowired private JobLauncherTestUtils jobLauncherTestUtils;

  @Autowired
  @Qualifier("ctrDetectionJob")
  private Job ctrDetectionJob;

  @Autowired private CustomerRepository customerRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private CtrReportQueueRepository ctrReportQueueRepository;

  @BeforeEach
  void setJob() {
    jobLauncherTestUtils.setJob(ctrDetectionJob);
  }

  @Test
  void 하루_현금거래_합계가_기준_미만이면_적재하지_않는다() throws Exception {
    Long customerId = saveCustomer();
    Account account = saveAccount(customerId);
    saveCompletedCashTransaction(
        TransactionType.DEPOSIT, null, account, new BigDecimal("500"), "2026-08-12T10:00:00+09:00");

    JobExecution execution =
        jobLauncherTestUtils.launchJob(
            new JobParametersBuilder().addString("runDate", "2026-08-12").toJobParameters());

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(
            ctrReportQueueRepository.existsByCustomerIdAndAccountIdAndTransactionDate(
                customerId, account.getAccountId(), LocalDate.of(2026, 8, 12)))
        .isFalse();
  }

  @Test
  void 같은_고객의_여러_계좌_합계가_기준을_넘으면_계좌별로_적재한다() throws Exception {
    Long customerId = saveCustomer();
    Account accountA = saveAccount(customerId);
    Account accountB = saveAccount(customerId);
    saveCompletedCashTransaction(
        TransactionType.DEPOSIT,
        null,
        accountA,
        new BigDecimal("600"),
        "2026-08-13T09:00:00+09:00");
    saveCompletedCashTransaction(
        TransactionType.WITHDRAWAL,
        accountB,
        null,
        new BigDecimal("500"),
        "2026-08-13T15:00:00+09:00");

    jobLauncherTestUtils.launchJob(
        new JobParametersBuilder().addString("runDate", "2026-08-13").toJobParameters());

    LocalDate transactionDate = LocalDate.of(2026, 8, 13);
    assertThat(
            ctrReportQueueRepository.existsByCustomerIdAndAccountIdAndTransactionDate(
                customerId, accountA.getAccountId(), transactionDate))
        .isTrue();
    assertThat(
            ctrReportQueueRepository.existsByCustomerIdAndAccountIdAndTransactionDate(
                customerId, accountB.getAccountId(), transactionDate))
        .isTrue();
    assertThat(ctrReportQueueRepository.findAll())
        .allMatch(report -> report.getStatus() == CtrReportStatus.LOGGED);
  }

  @Test
  void 같은_기준일_파라미터로_두번_실행하면_두번째_실행을_막는다() throws Exception {
    Long customerId = saveCustomer();
    saveAccount(customerId);

    JobParameters parameters =
        new JobParametersBuilder().addString("runDate", "2026-08-16").toJobParameters();
    jobLauncherTestUtils.launchJob(parameters);

    assertThatThrownBy(
            () ->
                jobLauncherTestUtils
                    .getJobLauncher()
                    .run(jobLauncherTestUtils.getJob(), parameters))
        .isInstanceOf(JobInstanceAlreadyCompleteException.class);
  }

  @Test
  void 같은_기준일로_재실행해도_중복_적재하지_않는다() throws Exception {
    Long customerId = saveCustomer();
    Account account = saveAccount(customerId);
    saveCompletedCashTransaction(
        TransactionType.DEPOSIT,
        null,
        account,
        new BigDecimal("1500"),
        "2026-08-14T10:00:00+09:00");

    jobLauncherTestUtils.launchJob(
        new JobParametersBuilder().addString("runDate", "2026-08-14").toJobParameters());
    jobLauncherTestUtils
        .getJobLauncher()
        .run(
            jobLauncherTestUtils.getJob(),
            jobLauncherTestUtils
                .getUniqueJobParametersBuilder()
                .addString("runDate", "2026-08-14")
                .toJobParameters());

    long count =
        ctrReportQueueRepository.findAll().stream()
            .filter(
                report ->
                    report.getCustomerId().equals(customerId)
                        && report.getAccountId().equals(account.getAccountId())
                        && report.getTransactionDate().equals(LocalDate.of(2026, 8, 14)))
            .count();
    assertThat(count).isEqualTo(1);
  }

  private void saveCompletedCashTransaction(
      TransactionType type, Account from, Account to, BigDecimal amount, String processedAt) {
    Transaction transaction =
        new Transaction(
            type,
            from == null ? null : from.getAccountId(),
            to == null ? null : to.getAccountId(),
            amount,
            "CTR-" + System.nanoTime(),
            "CTR 테스트");
    transaction = transactionRepository.save(transaction);
    transaction.complete(OffsetDateTime.parse(processedAt));
    transactionRepository.save(transaction);
  }

  private Long saveCustomer() {
    Customer customer =
        new Customer(
            "정민성",
            "ctr-user-" + System.nanoTime(),
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

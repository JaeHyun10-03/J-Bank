package com.jbank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountStatus;
import com.jbank.account.domain.AccountType;
import com.jbank.account.repository.AccountRepository;
import com.jbank.batch.reconciliation.LedgerReconciliationTasklet;
import com.jbank.customer.domain.Customer;
import com.jbank.customer.domain.CustomerStatus;
import com.jbank.customer.domain.IdentityVerificationMethod;
import com.jbank.customer.domain.KycGrade;
import com.jbank.customer.domain.RiskLevel;
import com.jbank.customer.repository.CustomerRepository;
import com.jbank.ledger.domain.EntryType;
import com.jbank.ledger.domain.LedgerEntry;
import com.jbank.ledger.repository.LedgerEntryRepository;
import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionType;
import com.jbank.transfer.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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

/** 원장 정합성 대사 배치 잡(구현계획 W5)이 불일치를 정확히 로그로 잡아내는지 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@SpringBatchTest
class LedgerReconciliationJobIntegrationTest {

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
  }

  @Autowired private JobLauncherTestUtils jobLauncherTestUtils;

  @Autowired
  @Qualifier("ledgerReconciliationJob")
  private Job ledgerReconciliationJob;

  @Autowired private CustomerRepository customerRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private LedgerEntryRepository ledgerEntryRepository;

  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    jobLauncherTestUtils.setJob(ledgerReconciliationJob);
    appender = new ListAppender<>();
    appender.start();
    ((Logger) LoggerFactory.getLogger(LedgerReconciliationTasklet.class)).addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    ((Logger) LoggerFactory.getLogger(LedgerReconciliationTasklet.class)).detachAppender(appender);
  }

  @Test
  void 캐시_잔액과_원장_합산이_일치하면_불일치_로그가_없다() throws Exception {
    Long customerId = saveCustomer();
    saveAccount(customerId, BigDecimal.ZERO);

    JobExecution execution =
        jobLauncherTestUtils.launchJob(
            new JobParametersBuilder().addString("runDate", "2026-08-12").toJobParameters());

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.WARN);
  }

  @Test
  void 계좌_캐시_잔액이_원장_합산과_다르면_불일치를_로그로_남긴다() throws Exception {
    Long customerId = saveCustomer();
    Account account = saveAccount(customerId, new BigDecimal("500.00"));

    jobLauncherTestUtils.launchJob(
        new JobParametersBuilder().addString("runDate", "2026-08-13").toJobParameters());

    assertThat(appender.list)
        .anyMatch(
            event ->
                event.getLevel() == Level.WARN
                    && event.getFormattedMessage().contains("계좌 잔액 불일치")
                    && event.getFormattedMessage().contains("accountId=" + account.getAccountId()));
  }

  @Test
  void 같은_기준일로_두번_실행하면_두번째_실행을_막는다() throws Exception {
    Long customerId = saveCustomer();
    saveAccount(customerId, BigDecimal.ZERO);

    JobParameters parameters =
        new JobParametersBuilder().addString("runDate", "2026-08-15").toJobParameters();
    jobLauncherTestUtils.launchJob(parameters);

    assertThatThrownBy(
            () ->
                jobLauncherTestUtils
                    .getJobLauncher()
                    .run(jobLauncherTestUtils.getJob(), parameters))
        .isInstanceOf(JobInstanceAlreadyCompleteException.class);
  }

  @Test
  void 전체_차변_대변_합이_다르면_불일치를_로그로_남긴다() throws Exception {
    Long customerId = saveCustomer();
    Account account = saveAccount(customerId, new BigDecimal("700.00"));
    Transaction transaction = saveCompletedTransaction(null, account, new BigDecimal("700.00"));
    saveLedgerEntry(account, transaction, EntryType.CREDIT, new BigDecimal("700.00"));

    jobLauncherTestUtils.launchJob(
        new JobParametersBuilder().addString("runDate", "2026-08-14").toJobParameters());

    assertThat(appender.list)
        .anyMatch(
            event ->
                event.getLevel() == Level.WARN
                    && event.getFormattedMessage().contains("전체 차변/대변 합 불일치"));
  }

  private Transaction saveCompletedTransaction(Account from, Account to, BigDecimal amount) {
    Transaction transaction =
        new Transaction(
            TransactionType.TRANSFER,
            from == null ? null : from.getAccountId(),
            to.getAccountId(),
            amount,
            "RECON-" + System.nanoTime(),
            "정합성 대사 테스트");
    transaction = transactionRepository.save(transaction);
    transaction.complete(OffsetDateTime.now());
    return transactionRepository.save(transaction);
  }

  private void saveLedgerEntry(
      Account account, Transaction transaction, EntryType entryType, BigDecimal amount) {
    ledgerEntryRepository.save(
        new LedgerEntry(
            account.getAccountId(),
            transaction.getTransactionId(),
            entryType,
            amount,
            account.getCurrentBalanceCache(),
            OffsetDateTime.now()));
  }

  private Long saveCustomer() {
    Customer customer =
        new Customer(
            "정민성",
            "recon-user-" + System.nanoTime(),
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

  private Account saveAccount(Long customerId, BigDecimal currentBalanceCache) {
    Account account =
        new Account(
            "110-" + System.nanoTime(),
            customerId,
            AccountType.CHECKING,
            AccountStatus.ACTIVE,
            currentBalanceCache,
            BigDecimal.ZERO,
            OffsetDateTime.now());
    return accountRepository.saveAndFlush(account);
  }
}

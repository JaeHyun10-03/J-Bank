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
import com.jbank.product.domain.ContractStatus;
import com.jbank.product.domain.Product;
import com.jbank.product.domain.ProductContract;
import com.jbank.product.domain.ProductStatus;
import com.jbank.product.repository.ProductContractRepository;
import com.jbank.product.repository.ProductRepository;
import com.jbank.transfer.domain.TransactionStatus;
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

/** 이자 계산·만기 처리 배치 잡(구현계획 W5)이 실제로 이자를 지급하고 재실행을 막는지 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@SpringBatchTest
class InterestMaturityJobIntegrationTest {

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
  @Qualifier("interestMaturityJob")
  private Job interestMaturityJob;

  @Autowired private ProductRepository productRepository;
  @Autowired private ProductContractRepository productContractRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private TransactionRepository transactionRepository;

  // 잡이 여러 개 등록된 애플리케이션 컨텍스트에서는 JobLauncherTestUtils가 잡을 자동으로
  // 못 찾으므로 명시적으로 지정한다.
  @BeforeEach
  void setJob() {
    jobLauncherTestUtils.setJob(interestMaturityJob);
  }

  @Test
  void 만기_도래한_계약에_이자를_지급하고_계약을_만기로_전환한다() throws Exception {
    Long customerId = saveCustomer();
    Account account = saveAccount(customerId);
    Product product = saveProduct("SAV-BATCH-001", new BigDecimal("0.0320"), 12);
    ProductContract contract =
        saveContract(
            customerId,
            product.getProductCode(),
            account.getAccountId(),
            new BigDecimal("1000000.00"),
            OffsetDateTime.parse("2026-08-11T00:00:00+09:00"));

    JobExecution execution =
        jobLauncherTestUtils.launchJob(
            new JobParametersBuilder().addString("runDate", "2026-08-11").toJobParameters());

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

    Account updatedAccount = accountRepository.findById(account.getAccountId()).orElseThrow();
    assertThat(updatedAccount.getCurrentBalanceCache()).isEqualByComparingTo("32000");

    ProductContract updatedContract =
        productContractRepository.findById(contract.getContractId()).orElseThrow();
    assertThat(updatedContract.getStatus()).isEqualTo(ContractStatus.MATURED);

    assertThat(
            transactionRepository.findByIdempotencyKey(
                "INTEREST-" + contract.getContractId() + "-2026-08-11"))
        .hasValueSatisfying(
            transaction -> {
              assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.INTEREST);
              assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
              assertThat(transaction.getAmount()).isEqualByComparingTo("32000");
            });
  }

  @Test
  void 만기가_아직_안된_계약은_건드리지_않는다() throws Exception {
    Long customerId = saveCustomer();
    Account account = saveAccount(customerId);
    Product product = saveProduct("SAV-BATCH-002", new BigDecimal("0.0320"), 12);
    ProductContract contract =
        saveContract(
            customerId,
            product.getProductCode(),
            account.getAccountId(),
            new BigDecimal("1000000.00"),
            OffsetDateTime.parse("2026-09-01T00:00:00+09:00"));

    jobLauncherTestUtils.launchJob(
        new JobParametersBuilder().addString("runDate", "2026-01-01").toJobParameters());

    Account untouchedAccount = accountRepository.findById(account.getAccountId()).orElseThrow();
    assertThat(untouchedAccount.getCurrentBalanceCache()).isEqualByComparingTo("0.00");

    ProductContract untouchedContract =
        productContractRepository.findById(contract.getContractId()).orElseThrow();
    assertThat(untouchedContract.getStatus()).isEqualTo(ContractStatus.ACTIVE);
  }

  @Test
  void 같은_기준일로_두번_실행하면_두번째_실행을_막는다() throws Exception {
    Long customerId = saveCustomer();
    Account account = saveAccount(customerId);
    Product product = saveProduct("SAV-BATCH-003", new BigDecimal("0.0320"), 12);
    saveContract(
        customerId,
        product.getProductCode(),
        account.getAccountId(),
        new BigDecimal("1000000.00"),
        OffsetDateTime.parse("2026-08-11T00:00:00+09:00"));

    JobParameters parameters =
        new JobParametersBuilder().addString("runDate", "2026-03-01").toJobParameters();
    jobLauncherTestUtils.launchJob(parameters);

    assertThatThrownBy(
            () ->
                jobLauncherTestUtils
                    .getJobLauncher()
                    .run(jobLauncherTestUtils.getJob(), parameters))
        .isInstanceOf(JobInstanceAlreadyCompleteException.class);
  }

  private Product saveProduct(
      String productCode, BigDecimal interestRate, int contractPeriodMonths) {
    return productRepository.saveAndFlush(
        new Product(
            productCode,
            "정기예금 " + contractPeriodMonths + "개월",
            interestRate,
            new BigDecimal("10000.00"),
            contractPeriodMonths,
            ProductStatus.ON_SALE));
  }

  private ProductContract saveContract(
      Long customerId,
      String productCode,
      Long accountId,
      BigDecimal subscriptionAmount,
      OffsetDateTime maturityAt) {
    return productContractRepository.saveAndFlush(
        new ProductContract(
            customerId,
            productCode,
            accountId,
            subscriptionAmount,
            maturityAt.minusMonths(12),
            maturityAt,
            ContractStatus.ACTIVE));
  }

  private Long saveCustomer() {
    Customer customer =
        new Customer(
            "정민성",
            "batch-user-" + System.nanoTime(),
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
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            OffsetDateTime.now());
    return accountRepository.saveAndFlush(account);
  }
}

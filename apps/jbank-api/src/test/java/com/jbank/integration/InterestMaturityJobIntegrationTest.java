package com.jbank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountStatus;
import com.jbank.account.domain.AccountType;
import com.jbank.account.repository.AccountRepository;
import com.jbank.batch.interest.MaturedContractApiClient;
import com.jbank.batch.interest.MaturedContractDto;
import com.jbank.customer.domain.Customer;
import com.jbank.customer.domain.CustomerStatus;
import com.jbank.customer.domain.IdentityVerificationMethod;
import com.jbank.customer.domain.KycGrade;
import com.jbank.customer.domain.RiskLevel;
import com.jbank.customer.repository.CustomerRepository;
import com.jbank.transfer.domain.TransactionStatus;
import com.jbank.transfer.domain.TransactionType;
import com.jbank.transfer.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 이자 계산·만기 처리 배치 잡(구현계획 W5)이 실제로 이자를 지급하고 재실행을 막는지 검증한다.
 *
 * <p>W7에서 product 모듈을 별도 배포 단위로 떼어내면서 만기 계약 조회·이자 계산은
 * jbank-product의 내부 API로 옮겨갔다(docs/adr/0007). 이 서비스 혼자서는 그 API를
 * 재현할 수 없으니 {@link MaturedContractApiClient}를 목으로 대체하고, 이 테스트는
 * "이자가 실제로 계좌·거래·원장에 반영되는지"만 계속 실제 Postgres로 검증한다 —
 * 검증 가치가 진짜 있는 부분(돈이 움직이는 로컬 트랜잭션)은 그대로 남기고, 이 서비스가
 * 더는 답할 수 없는 부분(만기 계약이 실제로 뭔지)만 목으로 대체한 것이다.
 */
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

  @Autowired private CustomerRepository customerRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private TransactionRepository transactionRepository;
  @MockitoBean private MaturedContractApiClient maturedContractApiClient;

  @BeforeEach
  void setJob() {
    jobLauncherTestUtils.setJob(interestMaturityJob);
  }

  @Test
  void 만기_도래한_계약에_이자를_지급하고_확정_API를_부른다() throws Exception {
    Long customerId = saveCustomer();
    Account account = saveAccount(customerId);
    given(maturedContractApiClient.findMatured(LocalDate.parse("2026-08-11")))
        .willReturn(List.of(new MaturedContractDto(1L, account.getAccountId(), new BigDecimal("32000"))));

    JobExecution execution =
        jobLauncherTestUtils.launchJob(
            new JobParametersBuilder().addString("runDate", "2026-08-11").toJobParameters());

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

    Account updatedAccount = accountRepository.findById(account.getAccountId()).orElseThrow();
    assertThat(updatedAccount.getCurrentBalanceCache()).isEqualByComparingTo("32000");

    assertThat(transactionRepository.findByIdempotencyKey("INTEREST-1"))
        .hasValueSatisfying(
            transaction -> {
              assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.INTEREST);
              assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
              assertThat(transaction.getAmount()).isEqualByComparingTo("32000");
            });
    then(maturedContractApiClient).should().markMatured(1L);
  }

  @Test
  void 만기_계약이_없으면_아무_계좌도_건드리지_않는다() throws Exception {
    given(maturedContractApiClient.findMatured(any())).willReturn(List.of());

    JobExecution execution =
        jobLauncherTestUtils.launchJob(
            new JobParametersBuilder().addString("runDate", "2026-01-01").toJobParameters());

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    then(maturedContractApiClient).should(org.mockito.Mockito.never()).markMatured(any());
  }

  @Test
  void 같은_기준일로_두번_실행하면_두번째_실행을_막는다() throws Exception {
    given(maturedContractApiClient.findMatured(any())).willReturn(List.of());

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

  @Test
  void 같은_계약이_이미_입금됐으면_확정_호출만_재시도하고_중복_입금하지_않는다() throws Exception {
    // 지난 실행에서 입금은 됐는데 markMatured 호출만 실패했던 상황을 흉내낸다 —
    // idempotencyKey가 계약당 하나뿐이라 재실행해도 크레딧이 한 번만 남아야 한다.
    Long customerId = saveCustomer();
    Account account = saveAccount(customerId);
    given(maturedContractApiClient.findMatured(LocalDate.parse("2026-08-12")))
        .willReturn(List.of(new MaturedContractDto(2L, account.getAccountId(), new BigDecimal("5000"))));
    jobLauncherTestUtils.launchJob(
        new JobParametersBuilder().addString("runDate", "2026-08-12").toJobParameters());

    given(maturedContractApiClient.findMatured(LocalDate.parse("2026-08-13")))
        .willReturn(List.of(new MaturedContractDto(2L, account.getAccountId(), new BigDecimal("5000"))));
    jobLauncherTestUtils
        .getJobLauncher()
        .run(
            jobLauncherTestUtils.getJob(),
            jobLauncherTestUtils
                .getUniqueJobParametersBuilder()
                .addString("runDate", "2026-08-13")
                .toJobParameters());

    Account updatedAccount = accountRepository.findById(account.getAccountId()).orElseThrow();
    assertThat(updatedAccount.getCurrentBalanceCache()).isEqualByComparingTo("5000"); // 중복 입금 안 됨
    // markMatured는 두 실행 모두에서 재시도로 호출된다 — 첫 실행에서 그 호출만
    // 실패했다고 가정한 시나리오이므로, 이걸 두 번 부르는 게 맞는 동작이다.
    then(maturedContractApiClient).should(org.mockito.Mockito.times(2)).markMatured(2L);
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

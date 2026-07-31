package com.jbank.concurrency;

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
import com.jbank.ledger.repository.LedgerEntryRepository;
import com.jbank.transfer.repository.TransactionRepository;
import com.jbank.transfer.service.DepositService;
import com.jbank.transfer.service.IdempotencyRecovery;
import com.jbank.transfer.service.TransferService;
import com.jbank.transfer.service.WithdrawalService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * W2 동시성 시나리오 5종의 공통 기반. 인메모리 H2로는 비관적 락 동작이 실제와 달라 검증 의미가 없어 Testcontainers로 실제 PostgreSQL을
 * 띄운다(구현계획 4절 W2). {@code @DataJpaTest}의 기본 트랜잭션 롤백을 {@code NOT_SUPPORTED}로 꺼야 여러 스레드가 각자 커밋한 결과를
 * 실제로 관찰할 수 있다 — 켜둔 채로는 테스트 스레드의 트랜잭션 하나로 묶여 동시성 검증 자체가 성립하지 않는다.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
  PiiEncryptionKeyHolder.class,
  HmacKeyHolder.class,
  IdempotencyRecovery.class,
  DepositService.class,
  WithdrawalService.class,
  TransferService.class
})
abstract class AbstractConcurrencyTest {

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

  @Autowired protected AccountRepository accountRepository;
  @Autowired protected CustomerRepository customerRepository;
  @Autowired protected TransactionRepository transactionRepository;
  @Autowired protected LedgerEntryRepository ledgerEntryRepository;
  @Autowired protected DepositService depositService;
  @Autowired protected WithdrawalService withdrawalService;
  @Autowired protected TransferService transferService;

  protected Account saveAccount(BigDecimal balance) {
    Long customerId = saveCustomer();
    Account account =
        new Account(
            "110-" + UUID.randomUUID().toString().substring(0, 9),
            customerId,
            AccountType.CHECKING,
            AccountStatus.ACTIVE,
            balance,
            BigDecimal.ZERO,
            OffsetDateTime.now());
    return accountRepository.saveAndFlush(account);
  }

  protected Long saveCustomer() {
    Customer customer =
        new Customer(
            "정민성",
            "900101-1234567",
            "hash-" + UUID.randomUUID(),
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

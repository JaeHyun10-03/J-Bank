package com.jbank.integration;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionStatus;
import com.jbank.transfer.domain.TransactionType;
import com.jbank.transfer.repository.TransactionRepository;
import com.jbank.transfer.service.PendingOtpCancellationService;
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
@Import({PiiEncryptionKeyHolder.class, HmacKeyHolder.class, PendingOtpCancellationService.class})
class PendingOtpCancellationServiceIntegrationTest {

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
  @Autowired private PendingOtpCancellationService pendingOtpCancellationService;

  @Test
  void 인증_대기_거래를_취소하면_거래는_CANCELLED로_지급정지는_해제된다() {
    Account from =
        saveAccount(new BigDecimal("20000000.00"), new BigDecimal("15000000.00"));
    Transaction transaction = savePendingOtpTransaction(from.getAccountId(), "15000000.00");

    pendingOtpCancellationService.cancel(transaction.getTransactionId());

    Transaction updated =
        transactionRepository.findById(transaction.getTransactionId()).orElseThrow();
    Account updatedFrom = accountRepository.findById(from.getAccountId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(TransactionStatus.CANCELLED);
    assertThat(updatedFrom.getHoldAmount()).isEqualByComparingTo("0.00");
    assertThat(updatedFrom.getCurrentBalanceCache()).isEqualByComparingTo("20000000.00");
  }

  @Test
  void 이미_PENDING_OTP가_아니면_아무것도_하지_않는다() {
    Account from = saveAccount(new BigDecimal("20000000.00"), BigDecimal.ZERO);
    Transaction transaction = savePendingOtpTransaction(from.getAccountId(), "15000000.00");
    transaction.cancel();
    transactionRepository.saveAndFlush(transaction);

    pendingOtpCancellationService.cancel(transaction.getTransactionId());

    Account updatedFrom = accountRepository.findById(from.getAccountId()).orElseThrow();
    assertThat(updatedFrom.getHoldAmount()).isEqualByComparingTo("0.00");
  }

  private Transaction savePendingOtpTransaction(Long fromAccountId, String amount) {
    Transaction transaction =
        new Transaction(
            TransactionType.TRANSFER,
            fromAccountId,
            null,
            new BigDecimal(amount),
            UUID.randomUUID().toString(),
            null);
    transaction.markPendingOtp();
    return transactionRepository.saveAndFlush(transaction);
  }

  private Account saveAccount(BigDecimal balance, BigDecimal holdAmount) {
    Long customerId = saveCustomer();
    Account account =
        new Account(
            "110-" + UUID.randomUUID().toString().substring(0, 9),
            customerId,
            AccountType.CHECKING,
            AccountStatus.ACTIVE,
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

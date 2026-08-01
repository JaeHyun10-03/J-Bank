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
import com.jbank.global.response.PageResponse;
import com.jbank.ledger.repository.LedgerEntryRepository;
import com.jbank.transfer.domain.TransactionType;
import com.jbank.transfer.dto.TransactionHistoryFilter;
import com.jbank.transfer.dto.TransactionSummaryResponse;
import com.jbank.transfer.repository.TransactionRepository;
import com.jbank.transfer.service.DepositService;
import com.jbank.transfer.service.IdempotencyRecovery;
import com.jbank.transfer.service.TransactionHistoryService;
import com.jbank.transfer.service.TransferService;
import com.jbank.transfer.service.WithdrawalService;
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
import org.springframework.data.domain.PageRequest;
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
  DepositService.class,
  WithdrawalService.class,
  TransferService.class,
  TransactionHistoryService.class
})
class TransactionHistoryServiceIntegrationTest {

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
  @Autowired private WithdrawalService withdrawalService;
  @Autowired private TransferService transferService;
  @Autowired private TransactionHistoryService transactionHistoryService;

  @Test
  void 필터가_없으면_계좌에_관련된_모든_거래를_반환한다() {
    Account a = saveAccount(new BigDecimal("100000.00"));
    Account b = saveAccount(new BigDecimal("100000.00"));
    depositService.deposit(
        a.getAccountId(),
        new BigDecimal("1000.00"),
        UUID.randomUUID().toString(),
        a.getCustomerId());
    withdrawalService.withdraw(
        a.getAccountId(),
        new BigDecimal("500.00"),
        UUID.randomUUID().toString(),
        a.getCustomerId());
    transferService.transfer(
        a.getAccountNumber(),
        b.getAccountNumber(),
        new BigDecimal("2000.00"),
        UUID.randomUUID().toString(),
        null,
        a.getCustomerId());

    PageResponse<TransactionSummaryResponse> history =
        transactionHistoryService.getHistory(
            a.getAccountId(), null, null, null, PageRequest.of(0, 20), a.getCustomerId());

    assertThat(history.totalElements()).isEqualTo(3);
  }

  @Test
  void DEPOSIT_필터는_입금만_반환한다() {
    Account a = saveAccount(new BigDecimal("100000.00"));
    depositService.deposit(
        a.getAccountId(),
        new BigDecimal("1000.00"),
        UUID.randomUUID().toString(),
        a.getCustomerId());
    withdrawalService.withdraw(
        a.getAccountId(),
        new BigDecimal("500.00"),
        UUID.randomUUID().toString(),
        a.getCustomerId());

    PageResponse<TransactionSummaryResponse> history =
        transactionHistoryService.getHistory(
            a.getAccountId(),
            TransactionHistoryFilter.DEPOSIT,
            null,
            null,
            PageRequest.of(0, 20),
            a.getCustomerId());

    assertThat(history.totalElements()).isEqualTo(1);
    assertThat(history.content().get(0).type()).isEqualTo(TransactionType.DEPOSIT);
  }

  @Test
  void TRANSFER_IN과_OUT은_계좌_기준_방향으로_구분된다() {
    Account a = saveAccount(new BigDecimal("100000.00"));
    Account b = saveAccount(new BigDecimal("100000.00"));
    transferService.transfer(
        a.getAccountNumber(),
        b.getAccountNumber(),
        new BigDecimal("2000.00"),
        UUID.randomUUID().toString(),
        null,
        a.getCustomerId());

    PageResponse<TransactionSummaryResponse> aOut =
        transactionHistoryService.getHistory(
            a.getAccountId(),
            TransactionHistoryFilter.TRANSFER_OUT,
            null,
            null,
            PageRequest.of(0, 20),
            a.getCustomerId());
    PageResponse<TransactionSummaryResponse> aIn =
        transactionHistoryService.getHistory(
            a.getAccountId(),
            TransactionHistoryFilter.TRANSFER_IN,
            null,
            null,
            PageRequest.of(0, 20),
            a.getCustomerId());
    PageResponse<TransactionSummaryResponse> bIn =
        transactionHistoryService.getHistory(
            b.getAccountId(),
            TransactionHistoryFilter.TRANSFER_IN,
            null,
            null,
            PageRequest.of(0, 20),
            b.getCustomerId());

    assertThat(aOut.totalElements()).isEqualTo(1);
    assertThat(aIn.totalElements()).isEqualTo(0);
    assertThat(bIn.totalElements()).isEqualTo(1);
  }

  private Account saveAccount(BigDecimal balance) {
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

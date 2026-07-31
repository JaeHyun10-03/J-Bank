package com.jbank.account.service;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountException;
import com.jbank.account.domain.AccountNumberGenerator;
import com.jbank.account.domain.AccountStatus;
import com.jbank.account.dto.AccountCloseResponse;
import com.jbank.account.dto.AccountDetailResponse;
import com.jbank.account.dto.AccountOpenRequest;
import com.jbank.account.dto.AccountOpenResponse;
import com.jbank.account.dto.AccountStatusChangeRequest;
import com.jbank.account.dto.AccountStatusChangeResponse;
import com.jbank.account.dto.BalanceResponse;
import com.jbank.account.dto.CustomerAccountSummaryResponse;
import com.jbank.account.repository.AccountRepository;
import com.jbank.customer.domain.Customer;
import com.jbank.customer.domain.CustomerStatus;
import com.jbank.customer.domain.KycGrade;
import com.jbank.customer.repository.CustomerRepository;
import com.jbank.customer.repository.CustomerRiskAssessmentHistoryRepository;
import com.jbank.global.exception.ErrorCode;
import com.jbank.global.response.PageResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

  private final AccountRepository accountRepository;
  private final CustomerRepository customerRepository;
  private final CustomerRiskAssessmentHistoryRepository historyRepository;
  private final AccountNumberGenerator accountNumberGenerator;

  public AccountService(
      AccountRepository accountRepository,
      CustomerRepository customerRepository,
      CustomerRiskAssessmentHistoryRepository historyRepository,
      AccountNumberGenerator accountNumberGenerator) {
    this.accountRepository = accountRepository;
    this.customerRepository = customerRepository;
    this.historyRepository = historyRepository;
    this.accountNumberGenerator = accountNumberGenerator;
  }

  @Transactional
  public AccountOpenResponse open(AccountOpenRequest request) {
    Long customerId = Long.valueOf(request.customerId());
    Customer customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new AccountException(ErrorCode.COMMON_004_NOT_FOUND));

    if (customer.getStatus() != CustomerStatus.ACTIVE) {
      throw new AccountException(ErrorCode.ACC_006_CUSTOMER_STATUS_INVALID);
    }
    // ponytail: EDD 완료 여부를 별도 컬럼으로 추적하지 않고, 최초 SYSTEM 판정 이후 이력이
    // 추가로 쌓였는지로 대신 판단한다. eddCompletedAt 컬럼이 생기면 이 조건을 대체한다.
    if (customer.getKycGrade() == KycGrade.EDD
        && historyRepository.findByCustomerId(customerId).size() <= 1) {
      throw new AccountException(ErrorCode.ACC_005_CDD_NOT_COMPLETED);
    }
    if (request.initialDeposit().compareTo(BigDecimal.ZERO) != 0) {
      throw new AccountException(ErrorCode.COMMON_001_VALIDATION_FAILED, "W1에서는 초기 입금 0원만 지원합니다");
    }

    String accountNumber = accountNumberGenerator.generate();
    OffsetDateTime openedAt = OffsetDateTime.now();
    Account account =
        new Account(
            accountNumber,
            customerId,
            request.productType(),
            AccountStatus.ACTIVE,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            openedAt);
    Long accountId = accountRepository.save(account).getAccountId();

    return new AccountOpenResponse(
        String.valueOf(accountId), accountNumber, AccountStatus.ACTIVE, openedAt);
  }

  // ponytail: 인증 필터체인이 아직 없어(W3 예정) 소유자 확인용 customerId를 파라미터로
  // 직접 받는다. W3에서 세션에서 추출한 값으로 교체한다.
  @Transactional(readOnly = true)
  public AccountDetailResponse getDetail(Long accountId, Long requestingCustomerId) {
    Account account =
        accountRepository
            .findById(accountId)
            .orElseThrow(() -> new AccountException(ErrorCode.COMMON_004_NOT_FOUND));
    if (!account.getCustomerId().equals(requestingCustomerId)) {
      throw new AccountException(ErrorCode.COMMON_003_FORBIDDEN);
    }
    return new AccountDetailResponse(
        String.valueOf(account.getAccountId()),
        account.getAccountNumber(),
        String.valueOf(account.getCustomerId()),
        account.getAccountType(),
        account.getStatus(),
        account.getOpenedAt(),
        account.getClosedAt());
  }

  @Transactional
  public AccountStatusChangeResponse changeStatus(
      Long accountId, AccountStatusChangeRequest request) {
    Account account =
        accountRepository
            .findById(accountId)
            .orElseThrow(() -> new AccountException(ErrorCode.COMMON_004_NOT_FOUND));

    AccountStatus previousStatus = account.getStatus();
    if (!isTransitionAllowed(previousStatus, request.targetStatus())) {
      throw new AccountException(ErrorCode.ACC_007_INVALID_STATUS_TRANSITION);
    }
    account.changeStatus(request.targetStatus());

    return new AccountStatusChangeResponse(
        String.valueOf(accountId), previousStatus, request.targetStatus());
  }

  // ponytail: 인증이 없어(W3 예정) 소유자 확인용 customerId를 파라미터로 직접 받는다.
  @Transactional
  public AccountCloseResponse close(Long accountId, Long requestingCustomerId) {
    Account account =
        accountRepository
            .findById(accountId)
            .orElseThrow(() -> new AccountException(ErrorCode.COMMON_004_NOT_FOUND));
    if (!account.getCustomerId().equals(requestingCustomerId)) {
      throw new AccountException(ErrorCode.COMMON_003_FORBIDDEN);
    }
    if (account.getStatus() == AccountStatus.CLOSED) {
      throw new AccountException(ErrorCode.ACC_009_ACCOUNT_STATUS_INVALID);
    }
    if (account.getCurrentBalanceCache().compareTo(BigDecimal.ZERO) != 0) {
      throw new AccountException(ErrorCode.ACC_008_BALANCE_NOT_ZERO);
    }
    if (account.getHoldAmount().compareTo(BigDecimal.ZERO) != 0) {
      throw new AccountException(ErrorCode.ACC_010_HOLD_AMOUNT_REMAINS);
    }

    account.close();
    return new AccountCloseResponse(
        String.valueOf(accountId), account.getStatus(), account.getClosedAt());
  }

  // 잔액의 진실은 원장 합산이고 이 값은 캐시일 뿐이다(FR-TXN-004). 정합성 검증은 W5 배치가 맡는다.
  @Transactional(readOnly = true)
  public BalanceResponse getBalance(Long accountId) {
    Account account =
        accountRepository
            .findById(accountId)
            .orElseThrow(() -> new AccountException(ErrorCode.COMMON_004_NOT_FOUND));
    return new BalanceResponse(
        String.valueOf(account.getAccountId()),
        account.getCurrentBalanceCache(),
        account.getHoldAmount(),
        account.getAvailableBalance(),
        OffsetDateTime.now());
  }

  @Transactional(readOnly = true)
  public PageResponse<CustomerAccountSummaryResponse> listByCustomer(
      Long customerId, AccountStatus statusFilter, Pageable pageable) {
    Page<Account> page =
        statusFilter == null
            ? accountRepository.findByCustomerId(customerId, pageable)
            : accountRepository.findByCustomerIdAndStatus(customerId, statusFilter, pageable);
    return PageResponse.from(page.map(AccountService::toSummary));
  }

  private static CustomerAccountSummaryResponse toSummary(Account account) {
    OffsetDateTime asOf = OffsetDateTime.now();
    BigDecimal availableBalance =
        account.getCurrentBalanceCache().subtract(account.getHoldAmount());
    return new CustomerAccountSummaryResponse(
        String.valueOf(account.getAccountId()),
        account.getAccountNumber(),
        String.valueOf(account.getCustomerId()),
        account.getAccountType(),
        account.getStatus(),
        account.getOpenedAt(),
        account.getClosedAt(),
        account.getCurrentBalanceCache(),
        account.getHoldAmount(),
        availableBalance,
        asOf);
  }

  // 해지(CLOSED)는 API-005 전용이라 이 경로로는 진입/이탈 모두 허용하지 않는다.
  private static boolean isTransitionAllowed(AccountStatus from, AccountStatus to) {
    if (from == to || to == AccountStatus.CLOSED || from == AccountStatus.CLOSED) {
      return false;
    }
    return switch (from) {
      case ACTIVE -> to == AccountStatus.SUSPENDED || to == AccountStatus.DORMANT;
      case SUSPENDED -> to == AccountStatus.ACTIVE || to == AccountStatus.DORMANT;
      case DORMANT -> to == AccountStatus.ACTIVE;
      case CLOSED -> false;
    };
  }
}

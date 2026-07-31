package com.jbank.account.service;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountException;
import com.jbank.account.domain.AccountNumberGenerator;
import com.jbank.account.domain.AccountStatus;
import com.jbank.account.dto.AccountOpenRequest;
import com.jbank.account.dto.AccountOpenResponse;
import com.jbank.account.repository.AccountRepository;
import com.jbank.customer.domain.Customer;
import com.jbank.customer.domain.CustomerStatus;
import com.jbank.customer.domain.KycGrade;
import com.jbank.customer.repository.CustomerRepository;
import com.jbank.customer.repository.CustomerRiskAssessmentHistoryRepository;
import com.jbank.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
}

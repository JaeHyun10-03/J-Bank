package com.jbank.internal.controller;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountException;
import com.jbank.account.repository.AccountRepository;
import com.jbank.global.exception.ErrorCode;
import com.jbank.global.response.ApiResponse;
import com.jbank.internal.dto.InternalDepositRequest;
import com.jbank.internal.dto.InternalWithdrawRequest;
import com.jbank.internal.dto.InternalWithdrawResponse;
import com.jbank.transfer.dto.AccountTransactionResponse;
import com.jbank.transfer.service.DepositService;
import com.jbank.transfer.service.WithdrawalService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 간 호출 전용 API — jbank-product의 상품가입 사가(계약 생성 → 출금 → 확정)가
 * 계좌·거래·원장을 소유한 이 서비스에 출금/보상 출금(입금)을 요청할 때 쓴다. 고객 JWT가
 * 아니라 {@link com.jbank.internal.config.InternalApiKeyFilter}의 공유 비밀키로 인증한다.
 */
@Hidden // springdoc-ui에서 감춘다 — 고객 대상 API가 아님
@RestController
@RequestMapping("/internal/v1/accounts")
public class InternalAccountController {

  private final AccountRepository accountRepository;
  private final WithdrawalService withdrawalService;
  private final DepositService depositService;

  public InternalAccountController(
      AccountRepository accountRepository,
      WithdrawalService withdrawalService,
      DepositService depositService) {
    this.accountRepository = accountRepository;
    this.withdrawalService = withdrawalService;
    this.depositService = depositService;
  }

  @PostMapping("/withdraw-by-number")
  public ResponseEntity<ApiResponse<InternalWithdrawResponse>> withdrawByNumber(
      @Valid @RequestBody InternalWithdrawRequest request) {
    Account account =
        accountRepository
            .findByAccountNumberForUpdate(request.accountNumber())
            .orElseThrow(() -> new AccountException(ErrorCode.COMMON_004_NOT_FOUND));

    AccountTransactionResponse response =
        withdrawalService.withdraw(
            account.getAccountId(), request.amount(), request.idempotencyKey(), request.customerId());

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                new InternalWithdrawResponse(
                    account.getAccountId(), response.transactionId(), response.balanceAfter())));
  }

  @PostMapping("/{accountId}/deposit")
  public ResponseEntity<ApiResponse<AccountTransactionResponse>> deposit(
      @PathVariable Long accountId, @Valid @RequestBody InternalDepositRequest request) {
    AccountTransactionResponse response =
        depositService.deposit(
            accountId, request.amount(), request.idempotencyKey(), request.customerId());
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }
}

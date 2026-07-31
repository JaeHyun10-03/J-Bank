package com.jbank.transfer.controller;

import com.jbank.global.response.ApiResponse;
import com.jbank.transfer.dto.AccountTransactionResponse;
import com.jbank.transfer.dto.DepositRequest;
import com.jbank.transfer.service.DepositService;
import com.jbank.transfer.service.WithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "거래", description = "입금·출금·잔액·거래내역 API")
@RestController
@RequestMapping("/api/v1/accounts/{accountId}")
public class AccountTransactionController {

  private final DepositService depositService;
  private final WithdrawalService withdrawalService;

  public AccountTransactionController(
      DepositService depositService, WithdrawalService withdrawalService) {
    this.depositService = depositService;
    this.withdrawalService = withdrawalService;
  }

  @Operation(summary = "입금", description = "Idempotency-Key 헤더로 재요청을 멱등 처리합니다.")
  @PostMapping("/deposit")
  public ResponseEntity<ApiResponse<AccountTransactionResponse>> deposit(
      @PathVariable Long accountId,
      @Parameter(description = "클라이언트가 생성한 UUID") @RequestHeader("Idempotency-Key")
          String idempotencyKey,
      @Valid @RequestBody DepositRequest request) {
    AccountTransactionResponse response =
        depositService.deposit(accountId, request.amount(), idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @Operation(
      summary = "출금",
      description = "출금 가능 금액(잔액-지급정지금액) 기준으로 검증합니다. Idempotency-Key 헤더가 필수입니다.")
  @PostMapping("/withdraw")
  public ResponseEntity<ApiResponse<AccountTransactionResponse>> withdraw(
      @PathVariable Long accountId,
      @Parameter(description = "클라이언트가 생성한 UUID") @RequestHeader("Idempotency-Key")
          String idempotencyKey,
      @Valid @RequestBody DepositRequest request) {
    AccountTransactionResponse response =
        withdrawalService.withdraw(accountId, request.amount(), idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }
}

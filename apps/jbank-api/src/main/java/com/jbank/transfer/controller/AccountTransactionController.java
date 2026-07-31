package com.jbank.transfer.controller;

import com.jbank.global.response.ApiResponse;
import com.jbank.global.response.PageResponse;
import com.jbank.transfer.dto.AccountTransactionResponse;
import com.jbank.transfer.dto.DepositRequest;
import com.jbank.transfer.dto.TransactionHistoryFilter;
import com.jbank.transfer.dto.TransactionSummaryResponse;
import com.jbank.transfer.service.DepositService;
import com.jbank.transfer.service.TransactionHistoryService;
import com.jbank.transfer.service.WithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "거래", description = "입금·출금·잔액·거래내역 API")
@RestController
@RequestMapping("/api/v1/accounts/{accountId}")
public class AccountTransactionController {

  private final DepositService depositService;
  private final WithdrawalService withdrawalService;
  private final TransactionHistoryService transactionHistoryService;

  public AccountTransactionController(
      DepositService depositService,
      WithdrawalService withdrawalService,
      TransactionHistoryService transactionHistoryService) {
    this.depositService = depositService;
    this.withdrawalService = withdrawalService;
    this.transactionHistoryService = transactionHistoryService;
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

  @Operation(summary = "거래내역 조회", description = "type, from, to는 선택입니다. 목록 조회 공통 페이지 규칙을 따릅니다.")
  @GetMapping("/transactions")
  public ResponseEntity<ApiResponse<PageResponse<TransactionSummaryResponse>>> getHistory(
      @PathVariable Long accountId,
      @RequestParam(required = false) TransactionHistoryFilter type,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime to,
      Pageable pageable) {
    PageResponse<TransactionSummaryResponse> response =
        transactionHistoryService.getHistory(accountId, type, from, to, pageable);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

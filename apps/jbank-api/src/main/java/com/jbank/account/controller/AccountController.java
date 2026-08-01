package com.jbank.account.controller;

import com.jbank.account.dto.AccountCloseResponse;
import com.jbank.account.dto.AccountDetailResponse;
import com.jbank.account.dto.AccountOpenRequest;
import com.jbank.account.dto.AccountOpenResponse;
import com.jbank.account.dto.AccountStatusChangeRequest;
import com.jbank.account.dto.AccountStatusChangeResponse;
import com.jbank.account.dto.BalanceResponse;
import com.jbank.account.service.AccountService;
import com.jbank.auth.config.CurrentCustomerId;
import com.jbank.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "계좌", description = "계좌 개설·조회·상태변경·해지 API")
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

  private final AccountService accountService;

  public AccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  @Operation(
      summary = "계좌 개설",
      description =
          """
          고객의 CDD 완료 여부와 상태를 확인한 뒤 계좌를 개설합니다.
          W1 기준 초기 입금은 0원만 지원합니다(그 외 금액은 400).
          """)
  @PostMapping
  public ResponseEntity<ApiResponse<AccountOpenResponse>> open(
      @Valid @RequestBody AccountOpenRequest request, @CurrentCustomerId Long customerId) {
    AccountOpenResponse response = accountService.open(request, customerId);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @Operation(summary = "계좌 상세조회", description = "소유자가 아니면 403.")
  @GetMapping("/{accountId}")
  public ResponseEntity<ApiResponse<AccountDetailResponse>> getDetail(
      @PathVariable Long accountId, @CurrentCustomerId Long customerId) {
    AccountDetailResponse response = accountService.getDetail(accountId, customerId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  // ponytail: 운영자 계정·역할 모델이 아직 없어(구현계획 문서 어디에도 W3 범위로 명시되지
  // 않음) 인증된 사용자면 누구나 호출 가능하다. 운영자 로그인이 생기면 hasRole('OPERATOR')로
  // 좁힌다.
  @Operation(
      summary = "계좌 상태변경",
      description =
          """
          ACTIVE↔SUSPENDED, ACTIVE/SUSPENDED→DORMANT, DORMANT→ACTIVE만 허용합니다.
          CLOSED로의 전환은 이 API로 할 수 없습니다(해지 API를 쓰세요). 허용되지 않는
          전이는 409(ACC_007)가 납니다. 운영자 전용이지만 운영자 역할 모델이 아직 없어
          인증된 사용자면 누구나 호출할 수 있습니다.
          """)
  @PatchMapping("/{accountId}/status")
  public ResponseEntity<ApiResponse<AccountStatusChangeResponse>> changeStatus(
      @PathVariable Long accountId, @Valid @RequestBody AccountStatusChangeRequest request) {
    AccountStatusChangeResponse response = accountService.changeStatus(accountId, request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(
      summary = "잔액 조회",
      description = "balance는 잔액, availableBalance는 잔액에서 지급정지금액을 뺀 출금 가능 금액입니다.")
  @GetMapping("/{accountId}/balance")
  public ResponseEntity<ApiResponse<BalanceResponse>> getBalance(
      @PathVariable Long accountId, @CurrentCustomerId Long customerId) {
    return ResponseEntity.ok(ApiResponse.success(accountService.getBalance(accountId, customerId)));
  }

  @Operation(summary = "계좌 해지", description = "잔액과 지급정지 금액이 모두 0원이어야 해지할 수 있습니다.")
  @DeleteMapping("/{accountId}")
  public ResponseEntity<ApiResponse<AccountCloseResponse>> close(
      @PathVariable Long accountId, @CurrentCustomerId Long customerId) {
    AccountCloseResponse response = accountService.close(accountId, customerId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

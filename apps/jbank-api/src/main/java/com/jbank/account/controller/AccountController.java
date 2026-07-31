package com.jbank.account.controller;

import com.jbank.account.dto.AccountCloseResponse;
import com.jbank.account.dto.AccountDetailResponse;
import com.jbank.account.dto.AccountOpenRequest;
import com.jbank.account.dto.AccountOpenResponse;
import com.jbank.account.dto.AccountStatusChangeRequest;
import com.jbank.account.dto.AccountStatusChangeResponse;
import com.jbank.account.service.AccountService;
import com.jbank.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
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
      @Valid @RequestBody AccountOpenRequest request) {
    AccountOpenResponse response = accountService.open(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  // ponytail: 세션 인증이 없어(W3 예정) 소유자 확인용 customerId를 쿼리 파라미터로 받는다.
  @Operation(
      summary = "계좌 상세조회",
      description = "인증이 아직 없어 소유자 확인용 customerId를 쿼리 파라미터로 함께 보내야 합니다. 소유자가 아니면 403.")
  @GetMapping("/{accountId}")
  public ResponseEntity<ApiResponse<AccountDetailResponse>> getDetail(
      @PathVariable Long accountId,
      @Parameter(description = "임시 소유자 확인용 (W3에서 세션으로 대체 예정)") @RequestParam Long customerId) {
    AccountDetailResponse response = accountService.getDetail(accountId, customerId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(
      summary = "계좌 상태변경",
      description =
          """
          ACTIVE↔SUSPENDED, ACTIVE/SUSPENDED→DORMANT, DORMANT→ACTIVE만 허용합니다.
          CLOSED로의 전환은 이 API로 할 수 없습니다(해지 API를 쓰세요). 허용되지 않는
          전이는 409(ACC_007)가 납니다.
          """)
  @PatchMapping("/{accountId}/status")
  public ResponseEntity<ApiResponse<AccountStatusChangeResponse>> changeStatus(
      @PathVariable Long accountId, @Valid @RequestBody AccountStatusChangeRequest request) {
    AccountStatusChangeResponse response = accountService.changeStatus(accountId, request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(
      summary = "계좌 해지",
      description = "잔액과 지급정지 금액이 모두 0원이어야 해지할 수 있습니다. 소유자 확인용 customerId가 필요합니다.")
  @DeleteMapping("/{accountId}")
  public ResponseEntity<ApiResponse<AccountCloseResponse>> close(
      @PathVariable Long accountId,
      @Parameter(description = "임시 소유자 확인용 (W3에서 세션으로 대체 예정)") @RequestParam Long customerId) {
    AccountCloseResponse response = accountService.close(accountId, customerId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

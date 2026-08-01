package com.jbank.account.controller;

import com.jbank.account.domain.AccountException;
import com.jbank.account.domain.AccountStatus;
import com.jbank.account.dto.CustomerAccountSummaryResponse;
import com.jbank.account.service.AccountService;
import com.jbank.auth.config.CurrentCustomerId;
import com.jbank.global.exception.ErrorCode;
import com.jbank.global.response.ApiResponse;
import com.jbank.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// API-017. URL이 /api/v1/customers 하위지만 계좌 목록 조회라 account 패키지에 둔다.
// customer 패키지는 account에 의존할 수 없다는 규칙(ArchUnit) 때문에 CustomerController에는
// 넣을 수 없다.
@Tag(name = "계좌", description = "계좌 개설·조회·상태변경·해지 API")
@RestController
@RequestMapping("/api/v1/customers/{customerId}/accounts")
public class CustomerAccountController {

  private final AccountService accountService;

  public CustomerAccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  @Operation(
      summary = "고객별 계좌 목록 조회",
      description =
          """
          한 고객이 보유한 계좌를 페이지 단위로 조회합니다. status 쿼리 파라미터로
          특정 상태(ACTIVE, SUSPENDED 등)만 필터링할 수 있습니다. 원장이 아직 없어
          balance·holdAmount·availableBalance는 항상 0입니다(W2에서 실제 값 반영).
          """)
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<CustomerAccountSummaryResponse>>> list(
      @PathVariable Long customerId,
      @CurrentCustomerId Long requestingCustomerId,
      @Parameter(description = "필터링할 계좌 상태, 생략하면 전체 조회") @RequestParam(required = false)
          AccountStatus status,
      Pageable pageable) {
    if (!customerId.equals(requestingCustomerId)) {
      throw new AccountException(ErrorCode.COMMON_003_FORBIDDEN);
    }
    PageResponse<CustomerAccountSummaryResponse> response =
        accountService.listByCustomer(customerId, status, pageable);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

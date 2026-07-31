package com.jbank.account.controller;

import com.jbank.account.domain.AccountStatus;
import com.jbank.account.dto.CustomerAccountSummaryResponse;
import com.jbank.account.service.AccountService;
import com.jbank.global.response.ApiResponse;
import com.jbank.global.response.PageResponse;
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
@RestController
@RequestMapping("/api/v1/customers/{customerId}/accounts")
public class CustomerAccountController {

  private final AccountService accountService;

  public CustomerAccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<CustomerAccountSummaryResponse>>> list(
      @PathVariable Long customerId,
      @RequestParam(required = false) AccountStatus status,
      Pageable pageable) {
    PageResponse<CustomerAccountSummaryResponse> response =
        accountService.listByCustomer(customerId, status, pageable);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

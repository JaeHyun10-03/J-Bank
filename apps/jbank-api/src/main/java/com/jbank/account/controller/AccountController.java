package com.jbank.account.controller;

import com.jbank.account.dto.AccountDetailResponse;
import com.jbank.account.dto.AccountOpenRequest;
import com.jbank.account.dto.AccountOpenResponse;
import com.jbank.account.dto.AccountStatusChangeRequest;
import com.jbank.account.dto.AccountStatusChangeResponse;
import com.jbank.account.service.AccountService;
import com.jbank.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

  private final AccountService accountService;

  public AccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<AccountOpenResponse>> open(
      @Valid @RequestBody AccountOpenRequest request) {
    AccountOpenResponse response = accountService.open(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  // ponytail: 세션 인증이 없어(W3 예정) 소유자 확인용 customerId를 쿼리 파라미터로 받는다.
  @GetMapping("/{accountId}")
  public ResponseEntity<ApiResponse<AccountDetailResponse>> getDetail(
      @PathVariable Long accountId, @RequestParam Long customerId) {
    AccountDetailResponse response = accountService.getDetail(accountId, customerId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PatchMapping("/{accountId}/status")
  public ResponseEntity<ApiResponse<AccountStatusChangeResponse>> changeStatus(
      @PathVariable Long accountId, @Valid @RequestBody AccountStatusChangeRequest request) {
    AccountStatusChangeResponse response = accountService.changeStatus(accountId, request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

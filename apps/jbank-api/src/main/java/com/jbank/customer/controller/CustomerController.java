package com.jbank.customer.controller;

import com.jbank.customer.dto.CustomerRegisterRequest;
import com.jbank.customer.dto.CustomerRegisterResponse;
import com.jbank.customer.dto.EddRegisterRequest;
import com.jbank.customer.dto.EddRegisterResponse;
import com.jbank.customer.service.CustomerService;
import com.jbank.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "고객", description = "고객 등록(CDD)과 강화된 고객확인(EDD) API")
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

  private final CustomerService customerService;

  public CustomerController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @Operation(
      summary = "고객 등록",
      description =
          """
          신규 고객을 등록하고 CDD(고객확인)를 함께 수행합니다. 인증이 필요 없습니다.
          같은 실명번호로 다시 등록하면 409(ACC_001)가 납니다.
          응답의 eddRequired가 true면 계좌 개설 전에 /edd API를 먼저 호출해야 합니다.
          """)
  @PostMapping
  public ResponseEntity<ApiResponse<CustomerRegisterResponse>> register(
      @Valid @RequestBody CustomerRegisterRequest request) {
    CustomerRegisterResponse response = customerService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @Operation(
      summary = "강화된 고객확인(EDD) 등록",
      description =
          """
          고위험으로 분류된 고객만 호출할 수 있습니다. 고위험이 아닌 고객이면
          409(ACC_004)가 납니다. 정상 처리되면 계좌 개설 게이트가 풀립니다.
          """)
  @PostMapping("/{customerId}/edd")
  public ResponseEntity<ApiResponse<EddRegisterResponse>> registerEdd(
      @PathVariable Long customerId, @Valid @RequestBody EddRegisterRequest request) {
    EddRegisterResponse response = customerService.registerEdd(customerId, request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

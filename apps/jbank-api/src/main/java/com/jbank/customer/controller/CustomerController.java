package com.jbank.customer.controller;

import com.jbank.customer.dto.CustomerRegisterRequest;
import com.jbank.customer.dto.CustomerRegisterResponse;
import com.jbank.customer.dto.EddRegisterRequest;
import com.jbank.customer.dto.EddRegisterResponse;
import com.jbank.customer.service.CustomerService;
import com.jbank.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

  private final CustomerService customerService;

  public CustomerController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<CustomerRegisterResponse>> register(
      @Valid @RequestBody CustomerRegisterRequest request) {
    CustomerRegisterResponse response = customerService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @PostMapping("/{customerId}/edd")
  public ResponseEntity<ApiResponse<EddRegisterResponse>> registerEdd(
      @PathVariable Long customerId, @Valid @RequestBody EddRegisterRequest request) {
    EddRegisterResponse response = customerService.registerEdd(customerId, request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

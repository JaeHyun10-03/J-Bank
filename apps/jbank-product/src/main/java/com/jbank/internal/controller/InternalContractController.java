package com.jbank.internal.controller;

import com.jbank.global.response.ApiResponse;
import com.jbank.internal.dto.MaturedContractResponse;
import com.jbank.product.service.ProductService;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * jbank-api의 이자 지급 배치가 호출하는 내부 API — {@link
 * com.jbank.internal.config.InternalApiKeyFilter}의 공유 비밀키로만 인증한다.
 */
@Hidden
@RestController
@RequestMapping("/internal/v1/contracts")
public class InternalContractController {

  private final ProductService productService;

  public InternalContractController(ProductService productService) {
    this.productService = productService;
  }

  @GetMapping("/matured")
  public ApiResponse<List<MaturedContractResponse>> matured(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
    return ApiResponse.success(productService.findMatured(asOf));
  }

  @PatchMapping("/{contractId}/mature")
  public ApiResponse<Void> mature(@PathVariable Long contractId) {
    productService.markMatured(contractId);
    return ApiResponse.success(null);
  }
}

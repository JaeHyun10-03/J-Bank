package com.jbank.product.controller;

import com.jbank.auth.config.CurrentCustomerId;
import com.jbank.global.exception.ErrorCode;
import com.jbank.global.response.ApiResponse;
import com.jbank.global.response.PageResponse;
import com.jbank.product.domain.ProductException;
import com.jbank.product.dto.ContractSummaryResponse;
import com.jbank.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// API-019. URL이 /api/v1/customers 하위지만 가입계약 조회라 product 패키지에 둔다.
// customer 패키지는 product에 의존할 수 없다는 규칙(ArchUnit) 때문에 CustomerController에는
// 넣을 수 없다. CustomerAccountController와 동일한 패턴.
@Tag(name = "상품", description = "예적금 상품 목록·가입 API")
@RestController
@RequestMapping("/api/v1/customers/{customerId}/contracts")
public class CustomerContractController {

  private final ProductService productService;

  public CustomerContractController(ProductService productService) {
    this.productService = productService;
  }

  @Operation(summary = "고객별 가입 계약 조회", description = "한 고객이 가입한 상품 계약을 페이지 단위로 조회합니다.")
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<ContractSummaryResponse>>> list(
      @PathVariable Long customerId,
      @CurrentCustomerId Long requestingCustomerId,
      Pageable pageable) {
    if (!customerId.equals(requestingCustomerId)) {
      throw new ProductException(ErrorCode.COMMON_003_FORBIDDEN);
    }
    return ResponseEntity.ok(
        ApiResponse.success(productService.listContracts(customerId, pageable)));
  }
}

package com.jbank.product.controller;

import com.jbank.auth.config.CurrentCustomerId;
import com.jbank.global.response.ApiResponse;
import com.jbank.global.response.PageResponse;
import com.jbank.product.dto.ProductSubscribeRequest;
import com.jbank.product.dto.ProductSubscribeResponse;
import com.jbank.product.dto.ProductSummaryResponse;
import com.jbank.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "상품", description = "예적금 상품 목록·가입 API")
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

  private final ProductService productService;

  public ProductController(ProductService productService) {
    this.productService = productService;
  }

  @Operation(summary = "상품 목록 조회", description = "판매중인 상품만 반환합니다. 인증이 필요 없습니다.")
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> list(Pageable pageable) {
    return ResponseEntity.ok(ApiResponse.success(productService.list(pageable)));
  }

  @Operation(summary = "상품 가입", description = "최소 가입금액 미달(PRD_001) 또는 판매중지 상품(PRD_002)이면 거절합니다.")
  @PostMapping("/{productCode}/subscriptions")
  public ResponseEntity<ApiResponse<ProductSubscribeResponse>> subscribe(
      @PathVariable String productCode,
      @Valid @RequestBody ProductSubscribeRequest request,
      @CurrentCustomerId Long customerId) {
    ProductSubscribeResponse response = productService.subscribe(productCode, request, customerId);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }
}

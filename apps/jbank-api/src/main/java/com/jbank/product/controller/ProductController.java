package com.jbank.product.controller;

import com.jbank.global.response.ApiResponse;
import com.jbank.global.response.PageResponse;
import com.jbank.product.dto.ProductSummaryResponse;
import com.jbank.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}

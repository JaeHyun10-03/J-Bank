package com.jbank.product.service;

import com.jbank.global.response.PageResponse;
import com.jbank.product.domain.Product;
import com.jbank.product.domain.ProductStatus;
import com.jbank.product.dto.ProductSummaryResponse;
import com.jbank.product.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

  private final ProductRepository productRepository;

  public ProductService(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  @Transactional(readOnly = true)
  public PageResponse<ProductSummaryResponse> list(Pageable pageable) {
    Page<Product> page = productRepository.findByStatus(ProductStatus.ON_SALE, pageable);
    return PageResponse.from(page.map(ProductService::toSummary));
  }

  private static ProductSummaryResponse toSummary(Product product) {
    return new ProductSummaryResponse(
        product.getProductCode(),
        product.getProductName(),
        product.getInterestRate(),
        product.getMinSubscriptionAmount(),
        product.getContractPeriodMonths());
  }
}

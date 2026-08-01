package com.jbank.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbank.global.response.PageResponse;
import com.jbank.product.domain.Product;
import com.jbank.product.domain.ProductStatus;
import com.jbank.product.dto.ProductSummaryResponse;
import com.jbank.product.repository.ProductRepository;
import com.jbank.product.service.ProductService;
import java.math.BigDecimal;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ProductService.class)
class ProductServiceIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("jbank.crypto.pii-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    registry.add("jbank.crypto.hash-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

  @Autowired private ProductRepository productRepository;
  @Autowired private ProductService productService;

  @Test
  void 판매중인_상품만_목록에_나온다() {
    productRepository.saveAndFlush(
        new Product(
            "SAV-12M-001",
            "정기적금 12개월",
            new BigDecimal("0.0320"),
            new BigDecimal("100000.00"),
            12,
            ProductStatus.ON_SALE));
    productRepository.saveAndFlush(
        new Product(
            "SAV-OLD-001",
            "단종된 상품",
            new BigDecimal("0.0100"),
            new BigDecimal("10000.00"),
            6,
            ProductStatus.DISCONTINUED));

    PageResponse<ProductSummaryResponse> response = productService.list(PageRequest.of(0, 20));

    assertThat(response.totalElements()).isEqualTo(1);
    assertThat(response.content().get(0).productCode()).isEqualTo("SAV-12M-001");
  }
}

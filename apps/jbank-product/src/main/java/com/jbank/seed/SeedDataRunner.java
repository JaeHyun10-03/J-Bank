package com.jbank.seed;

import com.jbank.product.domain.Product;
import com.jbank.product.domain.ProductStatus;
import com.jbank.product.repository.ProductRepository;
import java.math.BigDecimal;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 시연용 상품 시드 데이터. `--spring.profiles.active=local,seed`로만 켜지고, 데이터가
 * 이미 있으면 아무것도 하지 않는다. 원래 jbank-api의 SeedDataRunner에 있던 것을
 * W7 모듈 분리로 이관했다 — 고객·계좌 시드는 jbank-api 쪽에 그대로 남는다.
 */
@Profile("seed")
@Component
public class SeedDataRunner implements ApplicationRunner {

  private final ProductRepository productRepository;

  public SeedDataRunner(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (productRepository.count() > 0) {
      return;
    }

    // 프론트 상품 화면(피그마 시안)이 기대하는 productCode로 맞춘다. 금리는 08_앱디자인노트
    // 문서의 표시값 중 기본금리만 반영(우대금리는 단일 필드로 표현할 수 없어 제외).
    productRepository.save(
        new Product(
            "j-kids",
            "J키즈 적금",
            new BigDecimal("0.0350"),
            new BigDecimal("10000.00"),
            60,
            ProductStatus.ON_SALE));
    productRepository.save(
        new Product(
            "j-farm",
            "J팜 농장",
            new BigDecimal("0.0300"),
            new BigDecimal("10000.00"),
            12,
            ProductStatus.ON_SALE));
  }
}

package com.jbank.batch.interest;

import com.jbank.global.exception.ErrorCode;
import com.jbank.product.domain.Product;
import com.jbank.product.domain.ProductContract;
import com.jbank.product.domain.ProductException;
import com.jbank.product.repository.ProductRepository;
import java.math.RoundingMode;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MaturedContractInterestItemProcessor
    implements ItemProcessor<ProductContract, MaturedContractInterest> {

  private final ProductRepository productRepository;
  private final RoundingMode roundingMode;

  public MaturedContractInterestItemProcessor(
      ProductRepository productRepository,
      @Value("${jbank.batch.interest.rounding-mode:DOWN}") String roundingMode) {
    this.productRepository = productRepository;
    this.roundingMode = RoundingMode.valueOf(roundingMode);
  }

  @Override
  public MaturedContractInterest process(ProductContract contract) {
    Product product =
        productRepository
            .findByProductCode(contract.getProductCode())
            .orElseThrow(() -> new ProductException(ErrorCode.COMMON_004_NOT_FOUND));
    return new MaturedContractInterest(
        contract,
        InterestCalculator.calculate(
            contract.getSubscriptionAmount(),
            product.getInterestRate(),
            product.getContractPeriodMonths(),
            roundingMode));
  }
}

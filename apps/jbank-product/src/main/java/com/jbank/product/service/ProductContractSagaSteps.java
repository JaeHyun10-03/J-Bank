package com.jbank.product.service;

import com.jbank.global.exception.ErrorCode;
import com.jbank.product.domain.ProductContract;
import com.jbank.product.domain.ProductException;
import com.jbank.product.dto.ProductSubscribeRequest;
import com.jbank.product.repository.ProductContractRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품가입 사가의 로컬 DB 단계만 따로 뗀 클래스. {@link ProductService}가 이 메서드들을
 * {@code this.method()}로 직접 부르면 같은 빈 안 self-invocation이라 Spring AOP 프록시가
 * 가로채지 못해 @Transactional이 통째로 무시된다 — 그래서 별도 빈으로 분리해서
 * 진짜 프록시를 거쳐 호출되게 한다.
 */
@Component
class ProductContractSagaSteps {

  private final ProductContractRepository productContractRepository;

  ProductContractSagaSteps(ProductContractRepository productContractRepository) {
    this.productContractRepository = productContractRepository;
  }

  @Transactional
  Long createPending(
      Long customerId,
      String productCode,
      ProductSubscribeRequest request,
      OffsetDateTime subscribedAt,
      OffsetDateTime maturityAt) {
    ProductContract pending =
        ProductContract.pending(
            customerId, productCode, request.subscriptionAmount(), subscribedAt, maturityAt);
    return productContractRepository.save(pending).getContractId();
  }

  @Transactional
  void deletePending(Long contractId) {
    productContractRepository.deleteById(contractId);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  void confirm(Long contractId, Long accountId) {
    ProductContract contract =
        productContractRepository
            .findById(contractId)
            .orElseThrow(() -> new ProductException(ErrorCode.COMMON_004_NOT_FOUND));
    contract.confirm(accountId);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  void markFailed(Long contractId, Long accountId) {
    ProductContract contract =
        productContractRepository
            .findById(contractId)
            .orElseThrow(() -> new ProductException(ErrorCode.COMMON_004_NOT_FOUND));
    contract.markFailed(accountId);
  }
}

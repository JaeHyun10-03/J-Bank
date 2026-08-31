package com.jbank.product.service;

import com.jbank.global.exception.ErrorCode;
import com.jbank.global.response.PageResponse;
import com.jbank.internal.dto.MaturedContractResponse;
import com.jbank.product.client.AccountServiceClient;
import com.jbank.product.client.AccountWithdrawResult;
import com.jbank.product.domain.ContractStatus;
import com.jbank.product.domain.Product;
import com.jbank.product.domain.ProductContract;
import com.jbank.product.domain.ProductException;
import com.jbank.product.domain.ProductStatus;
import com.jbank.product.dto.ContractSummaryResponse;
import com.jbank.product.dto.ProductSubscribeRequest;
import com.jbank.product.dto.ProductSubscribeResponse;
import com.jbank.product.dto.ProductSummaryResponse;
import com.jbank.product.batch.InterestCalculator;
import com.jbank.product.repository.ProductContractRepository;
import com.jbank.product.repository.ProductRepository;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 목록·조회는 이전과 같지만, 가입(subscribe)은 W7에서 오케스트레이션 사가로
 * 바뀌었다 — 계좌·거래·원장이 jbank-api 소유가 되면서 같은 로컬 트랜잭션 안에
 * 묶을 수 없어졌기 때문이다(docs/adr/0007-w7-product-module-separation.md).
 *
 * <p>사가 3단계: (1) 계약을 PENDING으로 먼저 커밋 — 이 지점 이후 죽어도 사가가
 * 중간에 멈췄다는 흔적이 남는다. (2) jbank-api에 출금 요청 — 실패하면 아직 아무
 * 돈도 안 움직였으니 PENDING 행을 지우고 끝낸다(보상 불필요). (3) 계약을 ACTIVE로
 * 확정 — 이 단계가 실패하면(예: DB 오류) 이미 나간 돈을 되돌리는 보상 거래(입금)를
 * 호출하고 계약을 FAILED로 남긴다. 로컬 DB 단계는 {@link ProductContractSagaSteps}에
 * 있다 — self-invocation으로 @Transactional 프록시가 무력화되는 걸 피하려고
 * 별도 빈으로 뺐다.
 */
@Service
public class ProductService {

  private static final Logger log = LoggerFactory.getLogger(ProductService.class);

  private final ProductRepository productRepository;
  private final ProductContractRepository productContractRepository;
  private final ProductContractSagaSteps sagaSteps;
  private final AccountServiceClient accountServiceClient;
  private final RoundingMode interestRoundingMode;

  public ProductService(
      ProductRepository productRepository,
      ProductContractRepository productContractRepository,
      ProductContractSagaSteps sagaSteps,
      AccountServiceClient accountServiceClient,
      @Value("${jbank.batch.interest.rounding-mode:DOWN}") String interestRoundingMode) {
    this.productRepository = productRepository;
    this.productContractRepository = productContractRepository;
    this.sagaSteps = sagaSteps;
    this.accountServiceClient = accountServiceClient;
    this.interestRoundingMode = RoundingMode.valueOf(interestRoundingMode);
  }

  @Transactional(readOnly = true)
  public PageResponse<ProductSummaryResponse> list(Pageable pageable) {
    Page<Product> page = productRepository.findByStatus(ProductStatus.ON_SALE, pageable);
    return PageResponse.from(page.map(ProductService::toSummary));
  }

  public ProductSubscribeResponse subscribe(
      String productCode, ProductSubscribeRequest request, Long requestingCustomerId) {
    Product product =
        productRepository
            .findByProductCode(productCode)
            .orElseThrow(() -> new ProductException(ErrorCode.COMMON_004_NOT_FOUND));
    if (product.getStatus() != ProductStatus.ON_SALE) {
      throw new ProductException(ErrorCode.PRD_002_PRODUCT_NOT_AVAILABLE);
    }
    if (request.subscriptionAmount().compareTo(product.getMinSubscriptionAmount()) < 0) {
      throw new ProductException(ErrorCode.PRD_001_MIN_AMOUNT_NOT_MET);
    }

    OffsetDateTime subscribedAt = OffsetDateTime.now();
    OffsetDateTime maturityAt = subscribedAt.plusMonths(product.getContractPeriodMonths());
    Long contractId =
        sagaSteps.createPending(requestingCustomerId, productCode, request, subscribedAt, maturityAt);

    String idempotencyKey = "SUBSCRIBE-" + contractId;
    AccountWithdrawResult withdrawal;
    try {
      withdrawal =
          accountServiceClient.withdraw(
              request.accountNumber(),
              request.subscriptionAmount(),
              idempotencyKey,
              requestingCustomerId);
    } catch (ProductException e) {
      // 출금 자체가 실패 — 아직 돈이 안 움직였으니 보상 없이 PENDING 행만 지운다.
      sagaSteps.deletePending(contractId);
      throw e;
    }

    confirmOrCompensate(contractId, withdrawal, request, requestingCustomerId, idempotencyKey);

    return new ProductSubscribeResponse(
        String.valueOf(contractId), productCode, subscribedAt, maturityAt);
  }

  private void confirmOrCompensate(
      Long contractId,
      AccountWithdrawResult withdrawal,
      ProductSubscribeRequest request,
      Long requestingCustomerId,
      String idempotencyKey) {
    try {
      sagaSteps.confirm(contractId, withdrawal.accountId());
    } catch (RuntimeException e) {
      log.error(
          "계약 확정 실패, 보상 거래(출금 롤백) 시작: contractId={}, accountId={}",
          contractId,
          withdrawal.accountId(),
          e);
      // 보상 거래도 같은 idempotencyKey를 쓴다 — 이 서비스가 이 실패 이후 다시 죽어도
      // 재시도가 중복 입금을 만들지 않는다.
      accountServiceClient.depositBack(
          withdrawal.accountId(),
          request.subscriptionAmount(),
          idempotencyKey + "-COMPENSATE",
          requestingCustomerId);
      sagaSteps.markFailed(contractId, withdrawal.accountId());
      throw new ProductException(
          ErrorCode.PRD_003_SUBSCRIPTION_FAILED, "계약 확정에 실패해 출금을 롤백했습니다");
    }
  }

  @Transactional(readOnly = true)
  public PageResponse<ContractSummaryResponse> listContracts(Long customerId, Pageable pageable) {
    Page<ProductContract> page =
        productContractRepository.findByCustomerIdAndStatusNot(
            customerId, ContractStatus.PENDING, pageable);
    return PageResponse.from(page.map(ProductService::toContractSummary));
  }

  /** jbank-api의 이자 지급 배치가 호출하는 내부 API. 요율은 이 서비스가 갖고 있으니 이자 금액까지 계산해서 돌려준다. */
  @Transactional(readOnly = true)
  public List<MaturedContractResponse> findMatured(LocalDate asOf) {
    // 기준일 당일 만기까지 포함하려고 다음날 자정을 배타적 상한으로 쓴다.
    OffsetDateTime exclusiveUpperBound =
        asOf.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
    List<ProductContract> matured =
        productContractRepository.findByStatusAndMaturityAtLessThan(
            ContractStatus.ACTIVE, exclusiveUpperBound);
    return matured.stream().map(this::toMaturedContractResponse).toList();
  }

  private MaturedContractResponse toMaturedContractResponse(ProductContract contract) {
    Product product =
        productRepository
            .findByProductCode(contract.getProductCode())
            .orElseThrow(() -> new ProductException(ErrorCode.COMMON_004_NOT_FOUND));
    var interestAmount =
        InterestCalculator.calculate(
            contract.getSubscriptionAmount(),
            product.getInterestRate(),
            product.getContractPeriodMonths(),
            interestRoundingMode);
    return new MaturedContractResponse(contract.getContractId(), contract.getAccountId(), interestAmount);
  }

  /** jbank-api가 이자 입금을 마친 뒤 호출한다. 이미 MATURED면 재시도로 보고 그대로 둔다(멱등). */
  @Transactional
  public void markMatured(Long contractId) {
    ProductContract contract =
        productContractRepository
            .findById(contractId)
            .orElseThrow(() -> new ProductException(ErrorCode.COMMON_004_NOT_FOUND));
    if (contract.getStatus() == ContractStatus.MATURED) {
      return;
    }
    contract.markMatured();
  }

  private static ContractSummaryResponse toContractSummary(ProductContract contract) {
    return new ContractSummaryResponse(
        String.valueOf(contract.getContractId()),
        contract.getProductCode(),
        contract.getSubscriptionAmount(),
        contract.getSubscribedAt(),
        contract.getMaturityAt(),
        contract.getStatus());
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

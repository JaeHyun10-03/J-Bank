package com.jbank.product.service;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountException;
import com.jbank.account.domain.AccountStatus;
import com.jbank.account.repository.AccountRepository;
import com.jbank.global.exception.ErrorCode;
import com.jbank.global.response.PageResponse;
import com.jbank.product.domain.ContractStatus;
import com.jbank.product.domain.Product;
import com.jbank.product.domain.ProductContract;
import com.jbank.product.domain.ProductException;
import com.jbank.product.domain.ProductStatus;
import com.jbank.product.dto.ContractSummaryResponse;
import com.jbank.product.dto.ProductSubscribeRequest;
import com.jbank.product.dto.ProductSubscribeResponse;
import com.jbank.product.dto.ProductSummaryResponse;
import com.jbank.product.repository.ProductContractRepository;
import com.jbank.product.repository.ProductRepository;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

  private final ProductRepository productRepository;
  private final ProductContractRepository productContractRepository;
  private final AccountRepository accountRepository;

  public ProductService(
      ProductRepository productRepository,
      ProductContractRepository productContractRepository,
      AccountRepository accountRepository) {
    this.productRepository = productRepository;
    this.productContractRepository = productContractRepository;
    this.accountRepository = accountRepository;
  }

  @Transactional(readOnly = true)
  public PageResponse<ProductSummaryResponse> list(Pageable pageable) {
    Page<Product> page = productRepository.findByStatus(ProductStatus.ON_SALE, pageable);
    return PageResponse.from(page.map(ProductService::toSummary));
  }

  @Transactional
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

    Account account =
        accountRepository
            .findByAccountNumber(request.accountNumber())
            .orElseThrow(() -> new AccountException(ErrorCode.COMMON_004_NOT_FOUND));
    if (!account.getCustomerId().equals(requestingCustomerId)) {
      throw new AccountException(ErrorCode.COMMON_003_FORBIDDEN);
    }
    if (account.getStatus() != AccountStatus.ACTIVE) {
      throw new AccountException(ErrorCode.ACC_009_ACCOUNT_STATUS_INVALID);
    }

    OffsetDateTime subscribedAt = OffsetDateTime.now();
    OffsetDateTime maturityAt = subscribedAt.plusMonths(product.getContractPeriodMonths());
    ProductContract contract =
        new ProductContract(
            requestingCustomerId,
            productCode,
            account.getAccountId(),
            request.subscriptionAmount(),
            subscribedAt,
            maturityAt,
            ContractStatus.ACTIVE);
    Long contractId = productContractRepository.save(contract).getContractId();

    return new ProductSubscribeResponse(
        String.valueOf(contractId), productCode, subscribedAt, maturityAt);
  }

  @Transactional(readOnly = true)
  public PageResponse<ContractSummaryResponse> listContracts(Long customerId, Pageable pageable) {
    Page<ProductContract> page = productContractRepository.findByCustomerId(customerId, pageable);
    return PageResponse.from(page.map(ProductService::toContractSummary));
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

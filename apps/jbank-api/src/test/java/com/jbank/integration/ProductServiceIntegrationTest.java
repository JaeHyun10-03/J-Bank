package com.jbank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountException;
import com.jbank.account.domain.AccountStatus;
import com.jbank.account.domain.AccountType;
import com.jbank.account.repository.AccountRepository;
import com.jbank.common.crypto.HmacKeyHolder;
import com.jbank.common.crypto.PiiEncryptionKeyHolder;
import com.jbank.customer.domain.Customer;
import com.jbank.customer.domain.CustomerStatus;
import com.jbank.customer.domain.IdentityVerificationMethod;
import com.jbank.customer.domain.KycGrade;
import com.jbank.customer.domain.RiskLevel;
import com.jbank.customer.repository.CustomerRepository;
import com.jbank.global.exception.ErrorCode;
import com.jbank.global.response.PageResponse;
import com.jbank.product.domain.Product;
import com.jbank.product.domain.ProductException;
import com.jbank.product.domain.ProductStatus;
import com.jbank.product.dto.ProductSubscribeRequest;
import com.jbank.product.dto.ProductSubscribeResponse;
import com.jbank.product.dto.ProductSummaryResponse;
import com.jbank.product.repository.ProductContractRepository;
import com.jbank.product.repository.ProductRepository;
import com.jbank.product.service.ProductService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
@Import({PiiEncryptionKeyHolder.class, HmacKeyHolder.class, ProductService.class})
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
  @Autowired private ProductContractRepository productContractRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private AccountRepository accountRepository;
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

  @Test
  void 정상_가입이면_계약이_생성되고_만기일이_가입기간만큼_더해진다() {
    Product product = saveProduct("SAV-12M-002", 12, ProductStatus.ON_SALE);
    Long customerId = saveCustomer();
    Account account = saveAccount(customerId, AccountStatus.ACTIVE);

    ProductSubscribeResponse response =
        productService.subscribe(
            product.getProductCode(),
            new ProductSubscribeRequest(account.getAccountNumber(), new BigDecimal("100000.00")),
            customerId);

    assertThat(response.productCode()).isEqualTo(product.getProductCode());
    assertThat(response.maturityAt()).isEqualTo(response.subscribedAt().plusMonths(12));
    assertThat(productContractRepository.findByCustomerId(customerId, PageRequest.of(0, 20)))
        .hasSize(1);
  }

  @Test
  void 최소가입금액_미달이면_거절한다() {
    Product product = saveProduct("SAV-12M-003", 12, ProductStatus.ON_SALE);
    Long customerId = saveCustomer();
    Account account = saveAccount(customerId, AccountStatus.ACTIVE);

    assertThatThrownBy(
            () ->
                productService.subscribe(
                    product.getProductCode(),
                    new ProductSubscribeRequest(account.getAccountNumber(), new BigDecimal("1.00")),
                    customerId))
        .isInstanceOf(ProductException.class)
        .satisfies(
            ex ->
                assertThat(((ProductException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PRD_001_MIN_AMOUNT_NOT_MET));
  }

  @Test
  void 판매중지_상품이면_거절한다() {
    Product product = saveProduct("SAV-OLD-002", 12, ProductStatus.DISCONTINUED);
    Long customerId = saveCustomer();
    Account account = saveAccount(customerId, AccountStatus.ACTIVE);

    assertThatThrownBy(
            () ->
                productService.subscribe(
                    product.getProductCode(),
                    new ProductSubscribeRequest(
                        account.getAccountNumber(), new BigDecimal("100000.00")),
                    customerId))
        .isInstanceOf(ProductException.class)
        .satisfies(
            ex ->
                assertThat(((ProductException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PRD_002_PRODUCT_NOT_AVAILABLE));
  }

  @Test
  void 계좌소유자가_아니면_거절한다() {
    Product product = saveProduct("SAV-12M-004", 12, ProductStatus.ON_SALE);
    Long ownerId = saveCustomer();
    Long requesterId = saveCustomer();
    Account account = saveAccount(ownerId, AccountStatus.ACTIVE);

    assertThatThrownBy(
            () ->
                productService.subscribe(
                    product.getProductCode(),
                    new ProductSubscribeRequest(
                        account.getAccountNumber(), new BigDecimal("100000.00")),
                    requesterId))
        .isInstanceOf(AccountException.class)
        .satisfies(
            ex ->
                assertThat(((AccountException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.COMMON_003_FORBIDDEN));
  }

  @Test
  void 정지된_계좌는_가입할_수_없다() {
    Product product = saveProduct("SAV-12M-005", 12, ProductStatus.ON_SALE);
    Long customerId = saveCustomer();
    Account account = saveAccount(customerId, AccountStatus.SUSPENDED);

    assertThatThrownBy(
            () ->
                productService.subscribe(
                    product.getProductCode(),
                    new ProductSubscribeRequest(
                        account.getAccountNumber(), new BigDecimal("100000.00")),
                    customerId))
        .isInstanceOf(AccountException.class)
        .satisfies(
            ex ->
                assertThat(((AccountException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACC_009_ACCOUNT_STATUS_INVALID));
  }

  private Product saveProduct(String productCode, int contractPeriodMonths, ProductStatus status) {
    Product product =
        new Product(
            productCode,
            "정기적금 " + contractPeriodMonths + "개월",
            new BigDecimal("0.0320"),
            new BigDecimal("100000.00"),
            contractPeriodMonths,
            status);
    return productRepository.saveAndFlush(product);
  }

  private Long saveCustomer() {
    Customer customer =
        new Customer(
            "정민성",
            "user-" + System.nanoTime(),
            "hash-" + System.nanoTime(),
            "900101-1234567",
            "hash-" + System.nanoTime(),
            LocalDate.of(1990, 1, 1),
            "010-1234-5678",
            "서울특별시 강남구",
            "회사원",
            IdentityVerificationMethod.FACE_TO_FACE,
            OffsetDateTime.now(),
            KycGrade.GENERAL,
            RiskLevel.LOW,
            null,
            null,
            CustomerStatus.ACTIVE);
    return customerRepository.saveAndFlush(customer).getCustomerId();
  }

  private Account saveAccount(Long customerId, AccountStatus status) {
    Account account =
        new Account(
            "110-" + System.nanoTime(),
            customerId,
            AccountType.CHECKING,
            status,
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            OffsetDateTime.now());
    return accountRepository.saveAndFlush(account);
  }
}

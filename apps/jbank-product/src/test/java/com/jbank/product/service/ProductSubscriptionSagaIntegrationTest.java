package com.jbank.product.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willThrow;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.jbank.global.exception.ErrorCode;
import com.jbank.product.domain.ContractStatus;
import com.jbank.product.domain.Product;
import com.jbank.product.domain.ProductContract;
import com.jbank.product.domain.ProductException;
import com.jbank.product.domain.ProductStatus;
import com.jbank.product.dto.ProductSubscribeRequest;
import com.jbank.product.dto.ProductSubscribeResponse;
import com.jbank.product.repository.ProductContractRepository;
import com.jbank.product.repository.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 상품가입 사가(계약 생성 → 출금 → 확정)를 실제 HTTP 경계까지 포함해 검증한다.
 * jbank-api는 WireMock으로 흉내낸다 — 진짜 jbank-api를 띄우면 이 테스트가 두 서비스의
 * 컴파일 산출물에 모두 의존하게 돼 모듈 분리 취지와 어긋난다. 세 시나리오를 본다:
 * 정상 확정, 출금 자체 실패(보상 불필요, PENDING 삭제), 확정 실패(보상 거래로
 * 출금 롤백 — TODO가 명시한 검증 대상).
 */
@SpringBootTest
class ProductSubscriptionSagaIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
  static WireMockServer accountService;

  static {
    POSTGRES.start();
  }

  @BeforeAll
  static void startWireMock() {
    accountService = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    accountService.start();
  }

  @AfterAll
  static void stopWireMock() {
    accountService.stop();
  }

  @AfterEach
  void resetWireMock() {
    accountService.resetAll();
  }

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    // 실제 배포는 jbank-api의 Flyway 이력이 만든 스키마를 validate만 하지만, 테스트용
    // Testcontainers는 빈 DB라 여기서만 Hibernate가 스키마를 직접 만들게 한다.
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    registry.add("jbank.jwt.secret", () -> "test-secret-key-at-least-32-bytes-long-for-hs256");
    registry.add("jbank.internal.api-key", () -> "test-internal-key");
    registry.add("jbank.internal.account-service.base-url", () -> accountService.baseUrl());
  }

  @Autowired private ProductRepository productRepository;
  @Autowired private ProductContractRepository productContractRepository;
  @Autowired private ProductService productService;
  @MockitoSpyBean private ProductContractSagaSteps sagaSteps;

  @Test
  void 출금이_성공하면_계약이_ACTIVE로_확정된다() {
    Product product = saveProduct("SAV-SAGA-001");
    accountService.stubFor(
        post(urlEqualTo("/internal/v1/accounts/withdraw-by-number"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"success":true,"data":{"accountId":42,"transactionId":"txn-1","balanceAfter":"0.00"},"error":null}
                        """)));

    ProductSubscribeResponse response =
        productService.subscribe(
            product.getProductCode(),
            new ProductSubscribeRequest("110-0000000001", new BigDecimal("100000.00")),
            1L);

    ProductContract saved =
        productContractRepository.findById(Long.valueOf(response.contractNumber())).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(ContractStatus.ACTIVE);
    assertThat(saved.getAccountId()).isEqualTo(42L);
  }

  @Test
  void 출금이_실패하면_보상없이_PENDING_계약을_지운다() {
    Product product = saveProduct("SAV-SAGA-002");
    accountService.stubFor(
        post(urlEqualTo("/internal/v1/accounts/withdraw-by-number"))
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"success":false,"data":null,"error":{"code":"TXN_001_INSUFFICIENT_BALANCE","message":"출금 가능 금액을 초과했습니다"}}
                        """)));

    assertThatThrownBy(
            () ->
                productService.subscribe(
                    product.getProductCode(),
                    new ProductSubscribeRequest("110-0000000002", new BigDecimal("100000.00")),
                    1L))
        .isInstanceOf(ProductException.class)
        .satisfies(
            ex ->
                assertThat(((ProductException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PRD_003_SUBSCRIPTION_FAILED));

    assertThat(productContractRepository.findByCustomerIdAndStatusNot(1L, ContractStatus.MATURED, org.springframework.data.domain.PageRequest.of(0, 20)))
        .noneMatch(c -> c.getProductCode().equals(product.getProductCode()));
    accountService.verify(0, postRequestedFor(urlEqualTo("/internal/v1/accounts/42/deposit")));
  }

  @Test
  void 확정이_실패하면_보상_거래로_출금을_롤백하고_계약을_FAILED로_남긴다() {
    Product product = saveProduct("SAV-SAGA-003");
    accountService.stubFor(
        post(urlEqualTo("/internal/v1/accounts/withdraw-by-number"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"success":true,"data":{"accountId":77,"transactionId":"txn-2","balanceAfter":"0.00"},"error":null}
                        """)));
    accountService.stubFor(
        post(urlEqualTo("/internal/v1/accounts/77/deposit"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"success":true,"data":{"transactionId":"txn-3","accountId":"77","type":"DEPOSIT","amount":"100000.00","balanceAfter":"100000.00"},"error":null}
                        """)));
    // 확정 단계에서 DB 오류가 난 상황을 흉내낸다 — 실제 원인이 뭐든(제약 위반, 커넥션
    // 끊김 등) 보상 로직이 타야 하는 건 "확정이 실패했다"는 사실 자체이지 원인이 아니다.
    willThrow(new RuntimeException("확정 저장 실패(시뮬레이션)"))
        .given(sagaSteps)
        .confirm(anyLong(), anyLong());

    assertThatThrownBy(
            () ->
                productService.subscribe(
                    product.getProductCode(),
                    new ProductSubscribeRequest("110-0000000003", new BigDecimal("100000.00")),
                    1L))
        .isInstanceOf(ProductException.class)
        .satisfies(
            ex ->
                assertThat(((ProductException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PRD_003_SUBSCRIPTION_FAILED));

    // 보상 거래(출금 롤백)가 실제로 호출됐는지 — 이게 이번 주 완료 기준의 나머지 절반이다.
    accountService.verify(
        postRequestedFor(urlEqualTo("/internal/v1/accounts/77/deposit"))
            .withRequestBody(
                com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                    "$.amount", equalTo("100000.00"))));

    ProductContract failed =
        productContractRepository.findAll().stream()
            .filter(c -> c.getProductCode().equals(product.getProductCode()))
            .findFirst()
            .orElseThrow();
    assertThat(failed.getStatus()).isEqualTo(ContractStatus.FAILED);
    assertThat(failed.getAccountId()).isEqualTo(77L);
  }

  private Product saveProduct(String productCode) {
    return productRepository.saveAndFlush(
        new Product(
            productCode,
            "사가 테스트 상품",
            new BigDecimal("0.0320"),
            new BigDecimal("10000.00"),
            12,
            ProductStatus.ON_SALE));
  }
}

package com.jbank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jbank.account.domain.AccountType;
import com.jbank.account.dto.AccountOpenRequest;
import com.jbank.auth.dto.LoginRequest;
import com.jbank.customer.domain.IdentityVerificationMethod;
import com.jbank.customer.dto.CustomerRegisterRequest;
import com.jbank.product.domain.Product;
import com.jbank.product.domain.ProductStatus;
import com.jbank.product.dto.ProductSubscribeRequest;
import com.jbank.product.repository.ProductRepository;
import com.jbank.transfer.dto.DepositRequest;
import com.jbank.transfer.dto.TransferRequest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Phase 1 완료 기준(구현계획 4절 W3)이 요구하는 통합 시연 흐름 전체를 실제 HTTP 요청으로 검증한다: 회원가입→로그인→계좌개설→입금→이체→잔액조회→
 * 거래내역조회→상품가입. 계좌개설이 인증을 요구해 로그인이 계좌개설보다 먼저다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class FullFlowIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  static {
    POSTGRES.start();
    REDIS.start();
  }

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("jbank.crypto.pii-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    registry.add("jbank.crypto.hash-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    registry.add("jbank.jwt.secret", () -> "test-secret-key-at-least-32-bytes-long-for-hs256");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ProductRepository productRepository;

  @Test
  void 회원가입부터_상품가입까지_전체_흐름이_성공한다() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CustomerRegisterRequest(
                            "정민성",
                            "flow01",
                            "Passw0rd!23",
                            "9001011234567",
                            LocalDate.of(1990, 1, 1),
                            "010-1234-5678",
                            "서울특별시 강남구",
                            "회사원",
                            IdentityVerificationMethod.FACE_TO_FACE,
                            "생활비 관리",
                            "근로소득"))))
        .andExpect(status().isCreated());

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(new LoginRequest("flow01", "Passw0rd!23"))))
            .andExpect(status().isOk())
            .andReturn();
    Cookie accessToken = loginResult.getResponse().getCookie("access_token");
    Cookie csrfCookie = loginResult.getResponse().getCookie("XSRF-TOKEN");
    String csrfToken = csrfCookie.getValue();

    Long accountA = openAccount(accessToken, csrfCookie, csrfToken);
    Long accountB = openAccount(accessToken, csrfCookie, csrfToken);
    String accountNumberA = accountNumberOf(accountA, accessToken);
    String accountNumberB = accountNumberOf(accountB, accessToken);

    mockMvc
        .perform(
            post("/api/v1/accounts/" + accountA + "/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content(
                    objectMapper.writeValueAsString(
                        new DepositRequest(new BigDecimal("500000.00"), "ATM")))
                .cookie(accessToken, csrfCookie)
                .header("X-CSRF-TOKEN", csrfToken))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content(
                    objectMapper.writeValueAsString(
                        new TransferRequest(
                            accountNumberA, accountNumberB, new BigDecimal("100000.00"), "생활비")))
                .cookie(accessToken, csrfCookie)
                .header("X-CSRF-TOKEN", csrfToken))
        .andExpect(status().isCreated());

    JsonNode balanceA = getJson("/api/v1/accounts/" + accountA + "/balance", accessToken);
    assertThat(new BigDecimal(balanceA.get("data").get("balance").asText()))
        .isEqualByComparingTo("400000.00");
    JsonNode balanceB = getJson("/api/v1/accounts/" + accountB + "/balance", accessToken);
    assertThat(new BigDecimal(balanceB.get("data").get("balance").asText()))
        .isEqualByComparingTo("100000.00");

    JsonNode history = getJson("/api/v1/accounts/" + accountA + "/transactions", accessToken);
    assertThat(history.get("data").get("totalElements").asInt()).isEqualTo(2);

    productRepository.saveAndFlush(
        new Product(
            "SAV-12M-FLOW",
            "정기적금 12개월",
            new BigDecimal("0.0320"),
            new BigDecimal("100000.00"),
            12,
            ProductStatus.ON_SALE));

    mockMvc
        .perform(
            post("/api/v1/products/SAV-12M-FLOW/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ProductSubscribeRequest(accountNumberB, new BigDecimal("100000.00"))))
                .cookie(accessToken, csrfCookie)
                .header("X-CSRF-TOKEN", csrfToken))
        .andExpect(status().isCreated());

    JsonNode customerId =
        objectMapper.readTree(
            mockMvc
                .perform(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                new LoginRequest("flow01", "Passw0rd!23"))))
                .andReturn()
                .getResponse()
                .getContentAsString());
    Long loginCustomerId = customerId.get("data").get("customerId").asLong();

    JsonNode contracts =
        getJson("/api/v1/customers/" + loginCustomerId + "/contracts", accessToken);
    assertThat(contracts.get("data").get("totalElements").asInt()).isEqualTo(1);
    assertThat(contracts.get("data").get("content").get(0).get("productCode").asText())
        .isEqualTo("SAV-12M-FLOW");
  }

  private Long openAccount(Cookie accessToken, Cookie csrfCookie, String csrfToken)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new AccountOpenRequest(AccountType.CHECKING, BigDecimal.ZERO)))
                    .cookie(accessToken, csrfCookie)
                    .header("X-CSRF-TOKEN", csrfToken))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .get("data")
        .get("accountId")
        .asLong();
  }

  private String accountNumberOf(Long accountId, Cookie accessToken) throws Exception {
    JsonNode body = getJson("/api/v1/accounts/" + accountId, accessToken);
    return body.get("data").get("accountNumber").asText();
  }

  private JsonNode getJson(String path, Cookie accessToken) throws Exception {
    MvcResult result =
        mockMvc.perform(get(path).cookie(accessToken)).andExpect(status().isOk()).andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }
}

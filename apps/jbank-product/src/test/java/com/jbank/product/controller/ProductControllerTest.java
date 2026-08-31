package com.jbank.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jbank.auth.config.SecurityConfig;
import com.jbank.auth.jwt.JwtTokenProvider;
import com.jbank.global.response.PageResponse;
import com.jbank.product.dto.ProductSubscribeRequest;
import com.jbank.product.dto.ProductSubscribeResponse;
import com.jbank.product.dto.ProductSummaryResponse;
import com.jbank.product.service.ProductService;
import com.jbank.testsupport.AuthPostProcessors;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class})
@TestPropertySource(
    properties = "jbank.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256")
class ProductControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private ProductService productService;

  @Test
  void 인증_없이_상품목록을_조회하면_200이다() throws Exception {
    given(productService.list(any()))
        .willReturn(
            new PageResponse<>(
                List.of(
                    new ProductSummaryResponse(
                        "SAV-12M-001",
                        "정기적금 12개월",
                        new BigDecimal("0.0320"),
                        new BigDecimal("100000.00"),
                        12)),
                0,
                20,
                1,
                1));

    mockMvc
        .perform(get("/api/v1/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].productCode").value("SAV-12M-001"));
  }

  @Test
  void 인증된_고객이_상품가입을_요청하면_201이다() throws Exception {
    OffsetDateTime subscribedAt = OffsetDateTime.now();
    given(productService.subscribe(eq("SAV-12M-001"), any(), anyLong()))
        .willReturn(
            new ProductSubscribeResponse(
                "1", "SAV-12M-001", subscribedAt, subscribedAt.plusMonths(12)));

    mockMvc
        .perform(
            post("/api/v1/products/SAV-12M-001/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ProductSubscribeRequest("110-1234567890", new BigDecimal("100000.00"))))
                .with(AuthPostProcessors.asCustomer(1L))
                .with(AuthPostProcessors.csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.productCode").value("SAV-12M-001"));
  }
}

package com.jbank.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jbank.auth.config.SecurityConfig;
import com.jbank.auth.jwt.JwtTokenProvider;
import com.jbank.global.response.PageResponse;
import com.jbank.product.domain.ContractStatus;
import com.jbank.product.dto.ContractSummaryResponse;
import com.jbank.product.service.ProductService;
import com.jbank.testsupport.AuthPostProcessors;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerContractController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class})
@TestPropertySource(
    properties = "jbank.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256")
class CustomerContractControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ProductService productService;

  @Test
  void 고객의_가입계약을_조회하면_200과_페이지응답을_반환한다() throws Exception {
    ContractSummaryResponse summary =
        new ContractSummaryResponse(
            "1",
            "SAV-12M-001",
            new BigDecimal("100000.00"),
            OffsetDateTime.now(),
            OffsetDateTime.now().plusMonths(12),
            ContractStatus.ACTIVE);
    given(productService.listContracts(eq(1L), any()))
        .willReturn(new PageResponse<>(List.of(summary), 0, 20, 1, 1));

    mockMvc
        .perform(get("/api/v1/customers/1/contracts").with(AuthPostProcessors.asCustomer(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].productCode").value("SAV-12M-001"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void 다른_고객의_가입계약을_조회하면_403이다() throws Exception {
    mockMvc
        .perform(get("/api/v1/customers/1/contracts").with(AuthPostProcessors.asCustomer(2L)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("COMMON_003_FORBIDDEN"));
  }
}

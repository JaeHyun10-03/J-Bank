package com.jbank.account.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jbank.account.domain.AccountStatus;
import com.jbank.account.domain.AccountType;
import com.jbank.account.dto.CustomerAccountSummaryResponse;
import com.jbank.account.service.AccountService;
import com.jbank.auth.config.SecurityConfig;
import com.jbank.auth.jwt.JwtTokenProvider;
import com.jbank.global.response.PageResponse;
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

@WebMvcTest(CustomerAccountController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class})
@TestPropertySource(
    properties = "jbank.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256")
class CustomerAccountControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AccountService accountService;

  @Test
  void 고객의_계좌목록을_조회하면_200과_페이지응답을_반환한다() throws Exception {
    // given
    CustomerAccountSummaryResponse summary =
        new CustomerAccountSummaryResponse(
            "1",
            "110-000001-4",
            "1",
            AccountType.CHECKING,
            AccountStatus.ACTIVE,
            OffsetDateTime.now(),
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            OffsetDateTime.now());
    given(accountService.listByCustomer(eq(1L), isNull(), any()))
        .willReturn(new PageResponse<>(List.of(summary), 0, 20, 1, 1));

    // when & then
    mockMvc
        .perform(get("/api/v1/customers/1/accounts").with(AuthPostProcessors.asCustomer(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].accountNumber").value("110-000001-4"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void 다른_고객의_계좌목록을_조회하면_403이다() throws Exception {
    mockMvc
        .perform(get("/api/v1/customers/1/accounts").with(AuthPostProcessors.asCustomer(2L)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("COMMON_003_FORBIDDEN"));
  }
}

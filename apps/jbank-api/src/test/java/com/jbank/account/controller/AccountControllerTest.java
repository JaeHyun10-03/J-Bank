package com.jbank.account.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jbank.account.domain.AccountException;
import com.jbank.account.domain.AccountStatus;
import com.jbank.account.domain.AccountType;
import com.jbank.account.dto.AccountDetailResponse;
import com.jbank.account.dto.AccountOpenRequest;
import com.jbank.account.dto.AccountOpenResponse;
import com.jbank.account.service.AccountService;
import com.jbank.global.config.SecurityConfig;
import com.jbank.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
class AccountControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AccountService accountService;

  @Test
  void 유효한_요청이면_201과_개설결과를_반환한다() throws Exception {
    // given
    given(accountService.open(any()))
        .willReturn(
            new AccountOpenResponse(
                "1", "110-000001-4", AccountStatus.ACTIVE, OffsetDateTime.now()));

    // when & then
    mockMvc
        .perform(
            post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AccountOpenRequest("1", AccountType.CHECKING, BigDecimal.ZERO))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.accountNumber").value("110-000001-4"));
  }

  @Test
  void CDD_미완료면_403을_반환한다() throws Exception {
    // given
    given(accountService.open(any()))
        .willThrow(new AccountException(ErrorCode.ACC_005_CDD_NOT_COMPLETED));

    // when & then
    mockMvc
        .perform(
            post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AccountOpenRequest("1", AccountType.CHECKING, BigDecimal.ZERO))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("ACC_005_CDD_NOT_COMPLETED"));
  }

  @Test
  void 상세조회에_성공하면_200과_계좌정보를_반환한다() throws Exception {
    // given
    given(accountService.getDetail(eq(1L), eq(1L)))
        .willReturn(
            new AccountDetailResponse(
                "1",
                "110-000001-4",
                "1",
                AccountType.CHECKING,
                AccountStatus.ACTIVE,
                OffsetDateTime.now(),
                null));

    // when & then
    mockMvc
        .perform(get("/api/v1/accounts/1").param("customerId", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accountNumber").value("110-000001-4"));
  }

  @Test
  void 소유자가_아니면_403을_반환한다() throws Exception {
    // given
    given(accountService.getDetail(eq(1L), eq(2L)))
        .willThrow(new AccountException(ErrorCode.COMMON_003_FORBIDDEN));

    // when & then
    mockMvc
        .perform(get("/api/v1/accounts/1").param("customerId", "2"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("COMMON_003_FORBIDDEN"));
  }
}

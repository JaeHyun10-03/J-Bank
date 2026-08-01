package com.jbank.account.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jbank.account.domain.AccountException;
import com.jbank.account.domain.AccountStatus;
import com.jbank.account.domain.AccountType;
import com.jbank.account.dto.AccountCloseResponse;
import com.jbank.account.dto.AccountDetailResponse;
import com.jbank.account.dto.AccountOpenRequest;
import com.jbank.account.dto.AccountOpenResponse;
import com.jbank.account.dto.AccountStatusChangeRequest;
import com.jbank.account.dto.AccountStatusChangeResponse;
import com.jbank.account.dto.BalanceResponse;
import com.jbank.account.service.AccountService;
import com.jbank.auth.config.SecurityConfig;
import com.jbank.auth.jwt.JwtTokenProvider;
import com.jbank.global.exception.ErrorCode;
import com.jbank.testsupport.AuthPostProcessors;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class})
@TestPropertySource(
    properties = "jbank.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256")
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
                        new AccountOpenRequest("1", AccountType.CHECKING, BigDecimal.ZERO)))
                .with(AuthPostProcessors.asCustomer(1L))
                .with(AuthPostProcessors.csrf()))
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
                        new AccountOpenRequest("1", AccountType.CHECKING, BigDecimal.ZERO)))
                .with(AuthPostProcessors.asCustomer(1L))
                .with(AuthPostProcessors.csrf()))
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
        .perform(
            get("/api/v1/accounts/1")
                .param("customerId", "1")
                .with(AuthPostProcessors.asCustomer(1L)))
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
        .perform(
            get("/api/v1/accounts/1")
                .param("customerId", "2")
                .with(AuthPostProcessors.asCustomer(1L)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("COMMON_003_FORBIDDEN"));
  }

  @Test
  void 잔액조회에_성공하면_200과_잔액정보를_반환한다() throws Exception {
    // given
    given(accountService.getBalance(eq(1L)))
        .willReturn(
            new BalanceResponse(
                "1",
                new BigDecimal("1200000.00"),
                BigDecimal.ZERO,
                new BigDecimal("1200000.00"),
                OffsetDateTime.now()));

    // when & then
    mockMvc
        .perform(get("/api/v1/accounts/1/balance").with(AuthPostProcessors.asCustomer(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accountId").value("1"));
  }

  @Test
  void 상태변경에_성공하면_200과_변경결과를_반환한다() throws Exception {
    // given
    given(accountService.changeStatus(eq(1L), any()))
        .willReturn(
            new AccountStatusChangeResponse("1", AccountStatus.ACTIVE, AccountStatus.SUSPENDED));

    // when & then
    mockMvc
        .perform(
            patch("/api/v1/accounts/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AccountStatusChangeRequest(AccountStatus.SUSPENDED, "이상거래 의심")))
                .with(AuthPostProcessors.asCustomer(1L))
                .with(AuthPostProcessors.csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
  }

  @Test
  void 허용되지_않는_전이면_409를_반환한다() throws Exception {
    // given
    given(accountService.changeStatus(eq(1L), any()))
        .willThrow(new AccountException(ErrorCode.ACC_007_INVALID_STATUS_TRANSITION));

    // when & then
    mockMvc
        .perform(
            patch("/api/v1/accounts/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AccountStatusChangeRequest(AccountStatus.CLOSED, "임의 해지 시도")))
                .with(AuthPostProcessors.asCustomer(1L))
                .with(AuthPostProcessors.csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("ACC_007_INVALID_STATUS_TRANSITION"));
  }

  @Test
  void 해지에_성공하면_200과_해지결과를_반환한다() throws Exception {
    // given
    given(accountService.close(eq(1L), eq(1L)))
        .willReturn(new AccountCloseResponse("1", AccountStatus.CLOSED, OffsetDateTime.now()));

    // when & then
    mockMvc
        .perform(
            delete("/api/v1/accounts/1")
                .param("customerId", "1")
                .with(AuthPostProcessors.asCustomer(1L))
                .with(AuthPostProcessors.csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CLOSED"));
  }

  @Test
  void 잔액이_남아있으면_409를_반환한다() throws Exception {
    // given
    given(accountService.close(eq(1L), eq(1L)))
        .willThrow(new AccountException(ErrorCode.ACC_008_BALANCE_NOT_ZERO));

    // when & then
    mockMvc
        .perform(
            delete("/api/v1/accounts/1")
                .param("customerId", "1")
                .with(AuthPostProcessors.asCustomer(1L))
                .with(AuthPostProcessors.csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("ACC_008_BALANCE_NOT_ZERO"));
  }
}

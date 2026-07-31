package com.jbank.transfer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jbank.global.config.JacksonConfig;
import com.jbank.global.config.SecurityConfig;
import com.jbank.transfer.domain.TransactionType;
import com.jbank.transfer.dto.AccountTransactionResponse;
import com.jbank.transfer.dto.DepositRequest;
import com.jbank.transfer.service.DepositService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountTransactionController.class)
@Import({SecurityConfig.class, JacksonConfig.class})
class AccountTransactionControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private DepositService depositService;

  @Test
  void 유효한_요청이면_201과_입금결과를_반환한다() throws Exception {
    given(depositService.deposit(eq(1L), any(), eq("idem-key-1")))
        .willReturn(
            new AccountTransactionResponse(
                "10",
                "1",
                TransactionType.DEPOSIT,
                new BigDecimal("100000.00"),
                new BigDecimal("150000.00"),
                OffsetDateTime.now()));

    mockMvc
        .perform(
            post("/api/v1/accounts/1/deposit")
                .header("Idempotency-Key", "idem-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new DepositRequest(new BigDecimal("100000.00"), "INTERNET_BANKING"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.transactionId").value("10"))
        .andExpect(jsonPath("$.data.balanceAfter").value("150000.00"));
  }

  @Test
  void Idempotency_Key_헤더가_없으면_400이다() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/accounts/1/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new DepositRequest(new BigDecimal("100000.00"), "INTERNET_BANKING"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 금액이_0이하이면_400이다() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/accounts/1/deposit")
                .header("Idempotency-Key", "idem-key-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new DepositRequest(BigDecimal.ZERO, "INTERNET_BANKING"))))
        .andExpect(status().isBadRequest());
  }
}

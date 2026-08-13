package com.jbank.transfer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jbank.auth.config.SecurityConfig;
import com.jbank.auth.jwt.JwtTokenProvider;
import com.jbank.global.config.JacksonConfig;
import com.jbank.testsupport.AuthPostProcessors;
import com.jbank.transfer.domain.TransactionStatus;
import com.jbank.transfer.dto.TransferRequest;
import com.jbank.transfer.dto.TransferResponse;
import com.jbank.transfer.service.TransferService;
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

@WebMvcTest(TransferController.class)
@Import({SecurityConfig.class, JacksonConfig.class, JwtTokenProvider.class})
@TestPropertySource(
    properties = "jbank.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256")
class TransferControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private TransferService transferService;

  @Test
  void 유효한_요청이면_201과_이체결과를_반환한다() throws Exception {
    given(
            transferService.transfer(
                eq("110-000001-4"), eq("110-000002-1"), any(), eq("idem-key-1"), any(), eq(1L)))
        .willReturn(
            new TransferResponse(
                "20",
                TransactionStatus.COMPLETED,
                new BigDecimal("1200000.00"),
                OffsetDateTime.now()));

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Idempotency-Key", "idem-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new TransferRequest(
                            "110-000001-4", "110-000002-1", new BigDecimal("3000000.00"), "생활비")))
                .with(AuthPostProcessors.asCustomer(1L))
                .with(AuthPostProcessors.csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
        .andExpect(jsonPath("$.data.fromAccountBalanceAfter").value("1200000.00"));
  }

  @Test
  void 임계금액을_초과하면_202와_인증대기_상태를_반환한다() throws Exception {
    given(
            transferService.transfer(
                eq("110-000001-4"), eq("110-000002-1"), any(), eq("idem-key-otp"), any(), eq(1L)))
        .willReturn(
            new TransferResponse(
                "21", TransactionStatus.PENDING_OTP, new BigDecimal("1200000.00"), null));

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Idempotency-Key", "idem-key-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new TransferRequest(
                            "110-000001-4", "110-000002-1", new BigDecimal("15000000.00"), "생활비")))
                .with(AuthPostProcessors.asCustomer(1L))
                .with(AuthPostProcessors.csrf()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.status").value("PENDING_OTP"));
  }

  @Test
  void Idempotency_Key_헤더가_없으면_400이다() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new TransferRequest(
                            "110-000001-4", "110-000002-1", new BigDecimal("3000000.00"), null)))
                .with(AuthPostProcessors.asCustomer(1L))
                .with(AuthPostProcessors.csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 출금계좌번호가_없으면_400이다() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Idempotency-Key", "idem-key-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new TransferRequest(
                            "", "110-000002-1", new BigDecimal("3000000.00"), null)))
                .with(AuthPostProcessors.asCustomer(1L))
                .with(AuthPostProcessors.csrf()))
        .andExpect(status().isBadRequest());
  }
}

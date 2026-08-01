package com.jbank.customer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jbank.customer.domain.CustomerException;
import com.jbank.customer.domain.CustomerStatus;
import com.jbank.customer.domain.IdentityVerificationMethod;
import com.jbank.customer.domain.KycGrade;
import com.jbank.customer.domain.RiskLevel;
import com.jbank.customer.dto.CustomerRegisterRequest;
import com.jbank.customer.dto.CustomerRegisterResponse;
import com.jbank.customer.dto.EddRegisterRequest;
import com.jbank.customer.dto.EddRegisterResponse;
import com.jbank.customer.service.CustomerService;
import com.jbank.global.config.SecurityConfig;
import com.jbank.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerController.class)
@Import(SecurityConfig.class)
class CustomerControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private CustomerService customerService;

  private static CustomerRegisterRequest validRequest() {
    return new CustomerRegisterRequest(
        "정민성",
        "jungminsung01",
        "password123!",
        "900101-1234567",
        LocalDate.of(1990, 1, 1),
        "010-1234-5678",
        "서울특별시 강남구",
        "회사원",
        IdentityVerificationMethod.FACE_TO_FACE,
        null,
        null);
  }

  @Test
  void 유효한_요청이면_201과_등록결과를_반환한다() throws Exception {
    // given
    given(customerService.register(any()))
        .willReturn(
            new CustomerRegisterResponse(
                "1", KycGrade.GENERAL, RiskLevel.LOW, CustomerStatus.ACTIVE, false));

    // when & then
    mockMvc
        .perform(
            post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.customerId").value("1"))
        .andExpect(jsonPath("$.data.kycGrade").value("GENERAL"));
  }

  @Test
  void 이름이_비어있으면_400을_반환한다() throws Exception {
    // given
    CustomerRegisterRequest invalid =
        new CustomerRegisterRequest(
            "",
            "jungminsung01",
            "password123!",
            "900101-1234567",
            LocalDate.of(1990, 1, 1),
            "010-1234-5678",
            null,
            null,
            IdentityVerificationMethod.FACE_TO_FACE,
            null,
            null);

    // when & then
    mockMvc
        .perform(
            post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void 중복된_실명번호면_409를_반환한다() throws Exception {
    // given
    given(customerService.register(any()))
        .willThrow(new CustomerException(ErrorCode.ACC_001_DUPLICATE_RESIDENT_REG_NO));

    // when & then
    mockMvc
        .perform(
            post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("ACC_001_DUPLICATE_RESIDENT_REG_NO"));
  }

  @Test
  void EDD_등록에_성공하면_200과_결과를_반환한다() throws Exception {
    // given
    given(customerService.registerEdd(eq(1L), any()))
        .willReturn(new EddRegisterResponse("1", RiskLevel.HIGH, OffsetDateTime.now()));

    // when & then
    mockMvc
        .perform(
            post("/api/v1/customers/1/edd")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new EddRegisterRequest("해외 송금", "임대소득", "DOC-1"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.amlRiskLevel").value("HIGH"));
  }

  @Test
  void 고위험이_아닌_고객이면_409를_반환한다() throws Exception {
    // given
    given(customerService.registerEdd(eq(1L), any()))
        .willThrow(new CustomerException(ErrorCode.ACC_004_CUSTOMER_NOT_HIGH_RISK));

    // when & then
    mockMvc
        .perform(
            post("/api/v1/customers/1/edd")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new EddRegisterRequest("해외 송금", "임대소득", "DOC-1"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("ACC_004_CUSTOMER_NOT_HIGH_RISK"));
  }
}

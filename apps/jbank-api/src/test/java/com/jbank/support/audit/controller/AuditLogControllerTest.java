package com.jbank.support.audit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jbank.auth.config.SecurityConfig;
import com.jbank.auth.jwt.JwtTokenProvider;
import com.jbank.global.config.JacksonConfig;
import com.jbank.global.response.PageResponse;
import com.jbank.support.audit.domain.ActorType;
import com.jbank.support.audit.dto.AuditLogResponse;
import com.jbank.support.audit.service.AuditLogService;
import com.jbank.testsupport.AuthPostProcessors;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuditLogController.class)
@Import({SecurityConfig.class, JacksonConfig.class, JwtTokenProvider.class})
@TestPropertySource(properties = "jbank.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256")
class AuditLogControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuditLogService auditLogService;

  @Test
  void 감사로그_조회에_성공하면_200과_페이지결과를_반환한다() throws Exception {
    given(auditLogService.getLogs(isNull(), isNull(), isNull(), isNull(), any()))
        .willReturn(
            new PageResponse<>(
                List.of(
                    new AuditLogResponse(
                        1L,
                        "ACCOUNT_STATUS_CHANGED",
                        ActorType.SYSTEM,
                        null,
                        "ACCOUNT",
                        "1",
                        Map.of("previousStatus", "ACTIVE", "newStatus", "SUSPENDED"),
                        OffsetDateTime.now())),
                0,
                20,
                1,
                1));

    mockMvc
        .perform(get("/api/v1/admin/audit-logs").with(AuthPostProcessors.asCustomer(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].eventType").value("ACCOUNT_STATUS_CHANGED"));
  }

  @Test
  void 인증없이_요청하면_401을_반환한다() throws Exception {
    mockMvc.perform(get("/api/v1/admin/audit-logs")).andExpect(status().isUnauthorized());
  }

  @Test
  void 쿼리파라미터로_필터링하면_서비스에_그대로_전달한다() throws Exception {
    OffsetDateTime from = OffsetDateTime.parse("2026-08-01T00:00:00+09:00");
    OffsetDateTime to = OffsetDateTime.parse("2026-08-05T00:00:00+09:00");
    given(
            auditLogService.getLogs(
                eq("CUSTOMER_GRADE_CHANGED"), eq("OP-0007"), eq(from), eq(to), any()))
        .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

    mockMvc
        .perform(
            get("/api/v1/admin/audit-logs")
                .queryParam("eventType", "CUSTOMER_GRADE_CHANGED")
                .queryParam("actorId", "OP-0007")
                .queryParam("from", "2026-08-01T00:00:00+09:00")
                .queryParam("to", "2026-08-05T00:00:00+09:00")
                .with(AuthPostProcessors.asCustomer(1L)))
        .andExpect(status().isOk());
  }
}

package com.jbank.support.audit.controller;

import com.jbank.global.response.ApiResponse;
import com.jbank.global.response.PageResponse;
import com.jbank.support.audit.dto.AuditLogResponse;
import com.jbank.support.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "감사 로그", description = "운영자용 감사 로그 조회 API")
@RestController
@RequestMapping("/api/v1/admin")
public class AuditLogController {

  private final AuditLogService auditLogService;

  public AuditLogController(AuditLogService auditLogService) {
    this.auditLogService = auditLogService;
  }

  // ponytail: 운영자 계정·역할 모델이 아직 없어 인증된 사용자면 누구나 호출 가능하다.
  // 운영자 로그인이 생기면 hasRole('OPERATOR')로 좁힌다.
  @Operation(
      summary = "감사 로그 조회",
      description =
          """
          eventType, actorId, from, to는 선택입니다. 운영자 전용이지만 운영자 역할
          모델이 아직 없어 인증된 사용자면 누구나 호출할 수 있습니다.
          """)
  @GetMapping("/audit-logs")
  public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getAuditLogs(
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String actorId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime to,
      Pageable pageable) {
    PageResponse<AuditLogResponse> response =
        auditLogService.getLogs(eventType, actorId, from, to, pageable);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

package com.jbank.support.fds.controller;

import com.jbank.global.response.ApiResponse;
import com.jbank.global.response.PageResponse;
import com.jbank.support.fds.domain.FdsRuleType;
import com.jbank.support.fds.dto.SuspiciousTransactionResponse;
import com.jbank.support.fds.service.SuspiciousTransactionService;
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

@Tag(name = "이상거래 탐지", description = "운영자용 이상거래 탐지 결과 조회 API")
@RestController
@RequestMapping("/api/v1/admin")
public class SuspiciousTransactionController {

  private final SuspiciousTransactionService suspiciousTransactionService;

  public SuspiciousTransactionController(SuspiciousTransactionService suspiciousTransactionService) {
    this.suspiciousTransactionService = suspiciousTransactionService;
  }

  // ponytail: AuditLogController와 같은 이유 — 운영자 계정·역할 모델이 아직 없어
  // 인증된 사용자면 누구나 호출 가능하다. 운영자 로그인이 생기면 hasRole('OPERATOR')로 좁힌다.
  @Operation(
      summary = "이상거래 탐지 결과 조회(API-022)",
      description =
          """
          FR-SUP-003 룰 기반 이상거래 탐지 결과를 조회합니다. ruleType, from, to는 선택입니다.
          자동 차단은 하지 않고 조회만 제공합니다 — 운영자 전용이지만 운영자 역할 모델이
          아직 없어 인증된 사용자면 누구나 호출할 수 있습니다.
          """)
  @GetMapping("/suspicious-transactions")
  public ResponseEntity<ApiResponse<PageResponse<SuspiciousTransactionResponse>>> getSuspiciousTransactions(
      @RequestParam(required = false) FdsRuleType ruleType,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime to,
      Pageable pageable) {
    PageResponse<SuspiciousTransactionResponse> response =
        suspiciousTransactionService.getSuspiciousTransactions(ruleType, from, to, pageable);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

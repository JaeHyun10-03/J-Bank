package com.jbank.transfer.controller;

import com.jbank.auth.config.CurrentCustomerId;
import com.jbank.global.response.ApiResponse;
import com.jbank.transfer.domain.TransactionStatus;
import com.jbank.transfer.dto.OtpVerificationRequest;
import com.jbank.transfer.dto.TransferRequest;
import com.jbank.transfer.dto.TransferResponse;
import com.jbank.transfer.service.OtpVerificationService;
import com.jbank.transfer.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "이체", description = "계좌 이체 API")
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

  private final TransferService transferService;
  private final OtpVerificationService otpVerificationService;

  public TransferController(
      TransferService transferService, OtpVerificationService otpVerificationService) {
    this.transferService = transferService;
    this.otpVerificationService = otpVerificationService;
  }

  @Operation(
      summary = "계좌 이체",
      description =
          """
          두 계좌번호를 오름차순 정렬한 순서로 락을 획득해 교착상태를 막고,
          출금 가능 금액 기준으로 검증한 뒤 단일 트랜잭션으로 커밋합니다.
          임계금액을 초과하면 즉시 완료하지 않고 출금계좌에 지급정지를 반영한 뒤
          인증 대기(PENDING_OTP) 상태로 202를 반환합니다(FR-AUTH-003). OTP 검증(API-016)은
          별도 엔드포인트에서 처리합니다.
          """)
  @PostMapping
  public ResponseEntity<ApiResponse<TransferResponse>> transfer(
      @Parameter(description = "클라이언트가 생성한 UUID") @RequestHeader("Idempotency-Key")
          String idempotencyKey,
      @Valid @RequestBody TransferRequest request,
      @CurrentCustomerId Long customerId) {
    TransferResponse response =
        transferService.transfer(
            request.fromAccountNumber(),
            request.toAccountNumber(),
            request.amount(),
            idempotencyKey,
            request.memo(),
            customerId);
    HttpStatus status =
        response.status() == TransactionStatus.PENDING_OTP
            ? HttpStatus.ACCEPTED
            : HttpStatus.CREATED;
    return ResponseEntity.status(status).body(ApiResponse.success(response));
  }

  @Operation(
      summary = "고액이체 2차 인증(OTP) 검증",
      description =
          """
          임계금액 초과로 인증 대기(PENDING_OTP) 상태인 이체 건의 OTP를 검증합니다(FR-AUTH-003).
          성공하면 지급정지가 확정 출금으로 전환되며 이체가 완료됩니다. 실패 횟수가 한도를
          넘거나 유효시간(3분)이 지나면 거래가 취소되고 지급정지가 해제됩니다.
          """)
  @PostMapping("/{transactionId}/otp-verifications")
  public ResponseEntity<ApiResponse<TransferResponse>> verifyOtp(
      @PathVariable Long transactionId,
      @Valid @RequestBody OtpVerificationRequest request,
      @CurrentCustomerId Long customerId) {
    TransferResponse response =
        otpVerificationService.verify(transactionId, request.otpCode(), customerId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

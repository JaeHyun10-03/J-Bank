package com.jbank.transfer.controller;

import com.jbank.auth.config.CurrentCustomerId;
import com.jbank.global.response.ApiResponse;
import com.jbank.transfer.dto.TransferRequest;
import com.jbank.transfer.dto.TransferResponse;
import com.jbank.transfer.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

  public TransferController(TransferService transferService) {
    this.transferService = transferService;
  }

  @Operation(
      summary = "계좌 이체",
      description =
          """
          두 계좌번호를 오름차순 정렬한 순서로 락을 획득해 교착상태를 막고,
          출금 가능 금액 기준으로 검증한 뒤 단일 트랜잭션으로 커밋합니다.
          W2 기준 임계금액 초과에 따른 2차 인증 분기는 아직 없어 항상 즉시 완료됩니다(W5 예정).
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
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }
}

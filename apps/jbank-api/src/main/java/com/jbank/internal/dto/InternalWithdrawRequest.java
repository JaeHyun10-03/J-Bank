package com.jbank.internal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** jbank-product가 상품가입 사가의 출금 단계에서 보내는 요청. 계좌 소유주 검증까지 이 값들로 한다. */
public record InternalWithdrawRequest(
    @NotBlank String accountNumber,
    @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
    @NotBlank String idempotencyKey,
    @NotNull Long customerId) {}

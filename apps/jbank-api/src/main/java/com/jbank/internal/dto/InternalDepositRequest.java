package com.jbank.internal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 상품가입 사가의 보상 트랜잭션(출금 롤백) 요청. */
public record InternalDepositRequest(
    @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
    @NotBlank String idempotencyKey,
    @NotNull Long customerId) {}

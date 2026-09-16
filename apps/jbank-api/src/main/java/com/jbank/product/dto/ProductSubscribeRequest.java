package com.jbank.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ProductSubscribeRequest(
    @NotBlank String accountNumber,
    @NotNull @DecimalMin(value = "0.01", message = "가입금액은 0보다 커야 합니다")
        BigDecimal subscriptionAmount) {}

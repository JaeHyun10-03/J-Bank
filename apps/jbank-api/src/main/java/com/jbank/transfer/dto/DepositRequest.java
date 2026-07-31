package com.jbank.transfer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DepositRequest(
    @NotNull @DecimalMin(value = "0.01", message = "입금액은 0보다 커야 합니다") BigDecimal amount,
    @NotBlank(message = "channel은 필수입니다") String channel) {}

package com.jbank.transfer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record TransferRequest(
    @NotBlank String fromAccountNumber,
    @NotBlank String toAccountNumber,
    @NotNull @DecimalMin(value = "0.01", message = "이체 금액은 0보다 커야 합니다") BigDecimal amount,
    @Size(max = 200) String memo) {}

package com.jbank.account.dto;

import com.jbank.account.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AccountOpenRequest(
    @NotBlank String customerId,
    @NotNull AccountType productType,
    @NotNull BigDecimal initialDeposit) {}

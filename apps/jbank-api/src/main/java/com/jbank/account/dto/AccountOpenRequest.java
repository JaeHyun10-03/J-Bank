package com.jbank.account.dto;

import com.jbank.account.domain.AccountType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AccountOpenRequest(
    @NotNull AccountType productType, @NotNull BigDecimal initialDeposit) {}

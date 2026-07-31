package com.jbank.account.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BalanceResponse(
    String accountId,
    BigDecimal balance,
    BigDecimal holdAmount,
    BigDecimal availableBalance,
    OffsetDateTime asOf) {}

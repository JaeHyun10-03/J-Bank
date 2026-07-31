package com.jbank.account.dto;

import com.jbank.account.domain.AccountStatus;
import com.jbank.account.domain.AccountType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CustomerAccountSummaryResponse(
    String accountId,
    String accountNumber,
    String customerId,
    AccountType productType,
    AccountStatus status,
    OffsetDateTime openedAt,
    OffsetDateTime closedAt,
    BigDecimal balance,
    BigDecimal holdAmount,
    BigDecimal availableBalance,
    OffsetDateTime asOf) {}

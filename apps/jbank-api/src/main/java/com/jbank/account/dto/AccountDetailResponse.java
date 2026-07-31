package com.jbank.account.dto;

import com.jbank.account.domain.AccountStatus;
import com.jbank.account.domain.AccountType;
import java.time.OffsetDateTime;

public record AccountDetailResponse(
    String accountId,
    String accountNumber,
    String customerId,
    AccountType productType,
    AccountStatus status,
    OffsetDateTime openedAt,
    OffsetDateTime closedAt) {}

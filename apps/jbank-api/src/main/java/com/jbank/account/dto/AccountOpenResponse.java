package com.jbank.account.dto;

import com.jbank.account.domain.AccountStatus;
import java.time.OffsetDateTime;

public record AccountOpenResponse(
    String accountId, String accountNumber, AccountStatus status, OffsetDateTime openedAt) {}

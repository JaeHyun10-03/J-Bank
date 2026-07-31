package com.jbank.account.dto;

import com.jbank.account.domain.AccountStatus;
import java.time.OffsetDateTime;

public record AccountCloseResponse(
    String accountId, AccountStatus status, OffsetDateTime closedAt) {}

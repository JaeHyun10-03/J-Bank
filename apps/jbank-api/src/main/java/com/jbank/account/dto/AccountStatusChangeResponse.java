package com.jbank.account.dto;

import com.jbank.account.domain.AccountStatus;

public record AccountStatusChangeResponse(
    String accountId, AccountStatus previousStatus, AccountStatus status) {}

package com.jbank.account.dto;

import com.jbank.account.domain.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record AccountStatusChangeRequest(@NotNull AccountStatus targetStatus, String reason) {}

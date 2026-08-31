package com.jbank.internal.dto;

import java.math.BigDecimal;

public record InternalWithdrawResponse(
    Long accountId, String transactionId, BigDecimal balanceAfter) {}

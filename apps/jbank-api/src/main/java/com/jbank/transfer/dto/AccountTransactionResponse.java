package com.jbank.transfer.dto;

import com.jbank.transfer.domain.TransactionType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AccountTransactionResponse(
    String transactionId,
    String accountId,
    TransactionType type,
    BigDecimal amount,
    BigDecimal balanceAfter,
    OffsetDateTime processedAt) {}

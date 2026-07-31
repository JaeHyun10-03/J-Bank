package com.jbank.transfer.dto;

import com.jbank.transfer.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransferResponse(
    String transactionId,
    TransactionStatus status,
    BigDecimal fromAccountBalanceAfter,
    OffsetDateTime processedAt) {}

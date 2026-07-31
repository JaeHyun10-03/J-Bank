package com.jbank.transfer.dto;

import com.jbank.transfer.domain.TransactionStatus;
import com.jbank.transfer.domain.TransactionType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionSummaryResponse(
    String transactionId,
    TransactionType type,
    BigDecimal amount,
    TransactionStatus status,
    String memo,
    OffsetDateTime processedAt,
    OffsetDateTime createdAt) {}

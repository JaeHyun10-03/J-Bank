package com.jbank.common.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransferCompletedEvent(
    Long transactionId,
    Long fromAccountId,
    Long toAccountId,
    BigDecimal amount,
    OffsetDateTime occurredAt) {}

package com.jbank.support.fds.dto;

import com.jbank.support.fds.domain.FdsRuleType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SuspiciousTransactionResponse(
    String suspiciousTransactionId,
    String transactionId,
    String accountId,
    FdsRuleType ruleType,
    BigDecimal amount,
    String detail,
    OffsetDateTime detectedAt) {}

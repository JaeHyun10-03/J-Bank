package com.jbank.product.dto;

import com.jbank.product.domain.ContractStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ContractSummaryResponse(
    String contractNumber,
    String productCode,
    BigDecimal subscriptionAmount,
    OffsetDateTime subscribedAt,
    OffsetDateTime maturityAt,
    ContractStatus status) {}

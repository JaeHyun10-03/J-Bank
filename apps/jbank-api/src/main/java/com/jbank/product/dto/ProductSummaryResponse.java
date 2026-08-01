package com.jbank.product.dto;

import java.math.BigDecimal;

public record ProductSummaryResponse(
    String productCode,
    String productName,
    BigDecimal interestRate,
    BigDecimal minSubscriptionAmount,
    int contractPeriodMonths) {}

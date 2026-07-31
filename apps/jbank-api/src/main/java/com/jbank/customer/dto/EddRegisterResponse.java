package com.jbank.customer.dto;

import com.jbank.customer.domain.RiskLevel;
import java.time.OffsetDateTime;

public record EddRegisterResponse(
    String customerId, RiskLevel amlRiskLevel, OffsetDateTime eddCompletedAt) {}

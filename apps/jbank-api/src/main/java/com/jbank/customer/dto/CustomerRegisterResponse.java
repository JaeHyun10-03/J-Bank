package com.jbank.customer.dto;

import com.jbank.customer.domain.CustomerStatus;
import com.jbank.customer.domain.KycGrade;
import com.jbank.customer.domain.RiskLevel;

public record CustomerRegisterResponse(
    String customerId,
    KycGrade kycGrade,
    RiskLevel amlRiskLevel,
    CustomerStatus status,
    boolean eddRequired) {}

package com.jbank.customer.domain;

public record CddAssessmentResult(RiskLevel amlRiskLevel, KycGrade kycGrade, boolean eddRequired) {}

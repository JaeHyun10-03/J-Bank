package com.jbank.customer.domain;

import java.util.Objects;

public final class CddGradeCalculator {

  private CddGradeCalculator() {}

  // ponytail: occupation/거래목적 원문을 위험도로 분류하는 규칙표는 문서에 없어 범위 밖으로 둔다.
  // 호출 측이 이미 분류한 RiskLevel을 넘긴다고 가정한다. 분류 규칙표가 생기면 이 시그니처 앞단에 매퍼를 추가한다.
  public static CddAssessmentResult assess(
      RiskLevel occupationRiskLevel,
      RiskLevel transactionPurposeRiskLevel,
      IdentityVerificationMethod identityVerificationMethod) {
    Objects.requireNonNull(occupationRiskLevel, "occupationRiskLevel");
    Objects.requireNonNull(identityVerificationMethod, "identityVerificationMethod");

    RiskLevel purposeRisk =
        transactionPurposeRiskLevel == null ? RiskLevel.LOW : transactionPurposeRiskLevel;
    RiskLevel baseRisk = higherOf(occupationRiskLevel, purposeRisk);
    RiskLevel amlRiskLevel =
        identityVerificationMethod == IdentityVerificationMethod.NON_FACE_TO_FACE
            ? escalate(baseRisk)
            : baseRisk;
    KycGrade kycGrade = toKycGrade(amlRiskLevel);
    return new CddAssessmentResult(amlRiskLevel, kycGrade, kycGrade == KycGrade.EDD);
  }

  private static RiskLevel higherOf(RiskLevel a, RiskLevel b) {
    return a.ordinal() >= b.ordinal() ? a : b;
  }

  private static RiskLevel escalate(RiskLevel riskLevel) {
    return switch (riskLevel) {
      case LOW -> RiskLevel.MEDIUM;
      case MEDIUM, HIGH -> RiskLevel.HIGH;
    };
  }

  private static KycGrade toKycGrade(RiskLevel riskLevel) {
    return switch (riskLevel) {
      case LOW -> KycGrade.GENERAL;
      case MEDIUM -> KycGrade.CDD;
      case HIGH -> KycGrade.EDD;
    };
  }
}

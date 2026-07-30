package com.jbank.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CddGradeCalculatorTest {

  @Test
  void 직업과_거래목적_위험도가_모두_낮고_대면_확인이면_일반등급이다() {
    // given & when
    CddAssessmentResult result =
        CddGradeCalculator.assess(
            RiskLevel.LOW, RiskLevel.LOW, IdentityVerificationMethod.FACE_TO_FACE);

    // then
    assertThat(result.amlRiskLevel()).isEqualTo(RiskLevel.LOW);
    assertThat(result.kycGrade()).isEqualTo(KycGrade.GENERAL);
    assertThat(result.eddRequired()).isFalse();
  }

  @Test
  void 위험도는_직업과_거래목적_중_더_높은_쪽을_따른다() {
    // given & when
    CddAssessmentResult result =
        CddGradeCalculator.assess(
            RiskLevel.LOW, RiskLevel.HIGH, IdentityVerificationMethod.FACE_TO_FACE);

    // then
    assertThat(result.amlRiskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(result.kycGrade()).isEqualTo(KycGrade.EDD);
    assertThat(result.eddRequired()).isTrue();
  }

  @Test
  void 비대면_실명확인은_위험도를_한_단계_상향한다() {
    // given & when
    CddAssessmentResult result =
        CddGradeCalculator.assess(
            RiskLevel.LOW, RiskLevel.LOW, IdentityVerificationMethod.NON_FACE_TO_FACE);

    // then
    assertThat(result.amlRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
    assertThat(result.kycGrade()).isEqualTo(KycGrade.CDD);
  }

  @Test
  void 비대면_실명확인이어도_이미_고위험이면_고위험을_유지한다() {
    // given & when
    CddAssessmentResult result =
        CddGradeCalculator.assess(
            RiskLevel.HIGH, RiskLevel.LOW, IdentityVerificationMethod.NON_FACE_TO_FACE);

    // then
    assertThat(result.amlRiskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(result.kycGrade()).isEqualTo(KycGrade.EDD);
  }

  @Test
  void 거래목적_위험도가_없으면_LOW로_간주한다() {
    // given & when
    CddAssessmentResult result =
        CddGradeCalculator.assess(RiskLevel.MEDIUM, null, IdentityVerificationMethod.FACE_TO_FACE);

    // then
    assertThat(result.amlRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
    assertThat(result.kycGrade()).isEqualTo(KycGrade.CDD);
  }

  @Test
  void 직업_위험도가_없으면_예외를_던진다() {
    // given & when & then
    assertThatThrownBy(
            () ->
                CddGradeCalculator.assess(
                    null, RiskLevel.LOW, IdentityVerificationMethod.FACE_TO_FACE))
        .isInstanceOf(NullPointerException.class);
  }
}

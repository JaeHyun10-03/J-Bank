package com.jbank.product.batch;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 단리, 만기 시점 일시 지급 방식. 연이율 × 가입금액 × (계약기간월수 / 12), 원 단위는 설정된 규칙으로 절사/반올림한다. */
public final class InterestCalculator {

  private InterestCalculator() {}

  public static BigDecimal calculate(
      BigDecimal subscriptionAmount,
      BigDecimal annualInterestRate,
      int contractPeriodMonths,
      RoundingMode roundingMode) {
    return subscriptionAmount
        .multiply(annualInterestRate)
        .multiply(BigDecimal.valueOf(contractPeriodMonths))
        .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP)
        .setScale(0, roundingMode);
  }
}

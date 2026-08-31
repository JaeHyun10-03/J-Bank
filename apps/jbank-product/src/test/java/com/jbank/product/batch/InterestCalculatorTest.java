package com.jbank.product.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

class InterestCalculatorTest {

  @Test
  void 연이율과_계약기간으로_이자를_계산하고_절사한다() {
    BigDecimal interest =
        InterestCalculator.calculate(
            new BigDecimal("1000000.00"), new BigDecimal("0.0320"), 12, RoundingMode.DOWN);

    assertThat(interest).isEqualByComparingTo("32000");
  }

  @Test
  void 계약기간이_12개월_미만이면_월할로_계산한다() {
    BigDecimal interest =
        InterestCalculator.calculate(
            new BigDecimal("1000000.00"), new BigDecimal("0.0300"), 6, RoundingMode.DOWN);

    assertThat(interest).isEqualByComparingTo("15000");
  }

  @Test
  void 절사_규칙에_따라_원_단위_처리가_달라진다() {
    // 100000 × 0.0333 × 7 / 12 = 1942.5, DOWN은 절사·HALF_UP은 반올림이라 결과가 갈린다.
    BigDecimal down =
        InterestCalculator.calculate(
            new BigDecimal("100000.00"), new BigDecimal("0.0333"), 7, RoundingMode.DOWN);
    BigDecimal halfUp =
        InterestCalculator.calculate(
            new BigDecimal("100000.00"), new BigDecimal("0.0333"), 7, RoundingMode.HALF_UP);

    assertThat(down).isEqualByComparingTo("1942");
    assertThat(halfUp).isEqualByComparingTo("1943");
  }
}

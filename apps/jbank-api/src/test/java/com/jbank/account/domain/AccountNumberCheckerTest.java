package com.jbank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AccountNumberCheckerTest {

  @Test
  void 체크디지트는_표준_모듈러스10_공식으로_계산된다() {
    // given
    String digits = "7992739871";

    // when
    int checkDigit = AccountNumberChecker.calculateCheckDigit(digits);

    // then
    assertThat(checkDigit).isEqualTo(3);
  }

  @Test
  void 지점코드와_일련번호로_구성된_계좌번호에_올바른_체크디지트를_붙이면_검증을_통과한다() {
    // given
    String body = "1101234567";
    int checkDigit = AccountNumberChecker.calculateCheckDigit(body);

    // when
    boolean valid = AccountNumberChecker.isValid(body + checkDigit);

    // then
    assertThat(valid).isTrue();
  }

  @Test
  void 체크디지트가_틀리면_검증에_실패한다() {
    // given
    String body = "1101234567";
    int wrongCheckDigit = (AccountNumberChecker.calculateCheckDigit(body) + 1) % 10;

    // when
    boolean valid = AccountNumberChecker.isValid(body + wrongCheckDigit);

    // then
    assertThat(valid).isFalse();
  }

  @Test
  void 숫자가_아닌_문자가_포함되면_예외를_던진다() {
    // given
    String invalid = "110-123A";

    // when & then
    assertThatThrownBy(() -> AccountNumberChecker.calculateCheckDigit(invalid))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 체크디지트_한_자리뿐인_계좌번호는_검증에_실패한다() {
    // given
    String tooShort = "5";

    // when
    boolean valid = AccountNumberChecker.isValid(tooShort);

    // then
    assertThat(valid).isFalse();
  }
}

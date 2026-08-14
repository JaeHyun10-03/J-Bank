package com.jbank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AccountTest {

  @Test
  void hold하면_지급정지_금액이_늘고_잔액은_그대로다() {
    Account account = newAccount(new BigDecimal("10000.00"), new BigDecimal("1000.00"));

    account.hold(new BigDecimal("5000.00"));

    assertThat(account.getHoldAmount()).isEqualByComparingTo("6000.00");
    assertThat(account.getCurrentBalanceCache()).isEqualByComparingTo("10000.00");
    assertThat(account.getAvailableBalance()).isEqualByComparingTo("4000.00");
  }

  @Test
  void release하면_지급정지_금액이_줄고_잔액은_그대로다() {
    Account account = newAccount(new BigDecimal("10000.00"), new BigDecimal("6000.00"));

    account.release(new BigDecimal("5000.00"));

    assertThat(account.getHoldAmount()).isEqualByComparingTo("1000.00");
    assertThat(account.getCurrentBalanceCache()).isEqualByComparingTo("10000.00");
    assertThat(account.getAvailableBalance()).isEqualByComparingTo("9000.00");
  }

  private Account newAccount(BigDecimal balance, BigDecimal holdAmount) {
    return new Account(
        "110-123456789",
        1L,
        AccountType.CHECKING,
        AccountStatus.ACTIVE,
        balance,
        holdAmount,
        OffsetDateTime.now());
  }
}

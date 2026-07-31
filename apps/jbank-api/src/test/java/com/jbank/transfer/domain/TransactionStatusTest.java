package com.jbank.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TransactionStatusTest {

  @Test
  void PENDING에서_COMPLETED_PENDING_OTP_FAILED로만_전이할_수_있다() {
    assertThat(TransactionStatus.PENDING.canTransitionTo(TransactionStatus.COMPLETED)).isTrue();
    assertThat(TransactionStatus.PENDING.canTransitionTo(TransactionStatus.PENDING_OTP)).isTrue();
    assertThat(TransactionStatus.PENDING.canTransitionTo(TransactionStatus.FAILED)).isTrue();
    assertThat(TransactionStatus.PENDING.canTransitionTo(TransactionStatus.CANCELLED)).isFalse();
  }

  @Test
  void PENDING_OTP에서_COMPLETED_CANCELLED로만_전이할_수_있다() {
    assertThat(TransactionStatus.PENDING_OTP.canTransitionTo(TransactionStatus.COMPLETED)).isTrue();
    assertThat(TransactionStatus.PENDING_OTP.canTransitionTo(TransactionStatus.CANCELLED)).isTrue();
    assertThat(TransactionStatus.PENDING_OTP.canTransitionTo(TransactionStatus.FAILED)).isFalse();
  }

  @Test
  void 종료_상태에서는_어떤_전이도_허용하지_않는다() {
    for (TransactionStatus terminal :
        new TransactionStatus[] {
          TransactionStatus.COMPLETED, TransactionStatus.FAILED, TransactionStatus.CANCELLED
        }) {
      for (TransactionStatus target : TransactionStatus.values()) {
        assertThat(terminal.canTransitionTo(target)).isFalse();
      }
    }
  }
}

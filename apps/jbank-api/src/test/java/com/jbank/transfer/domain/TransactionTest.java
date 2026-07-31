package com.jbank.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionTest {

  @Test
  void 생성_직후_상태는_PENDING이다() {
    Transaction transaction = newTransaction();
    assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
  }

  @Test
  void complete를_호출하면_COMPLETED로_바뀌고_처리시각이_남는다() {
    Transaction transaction = newTransaction();
    OffsetDateTime processedAt = OffsetDateTime.now();

    transaction.complete(processedAt);

    assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    assertThat(transaction.getProcessedAt()).isEqualTo(processedAt);
  }

  @Test
  void 종료_상태에서_다시_완료_처리하면_예외를_던진다() {
    Transaction transaction = newTransaction();
    transaction.complete(OffsetDateTime.now());

    assertThatThrownBy(() -> transaction.complete(OffsetDateTime.now()))
        .isInstanceOf(IllegalStateException.class);
  }

  private Transaction newTransaction() {
    return new Transaction(
        TransactionType.TRANSFER,
        1L,
        2L,
        new BigDecimal("1000.00"),
        UUID.randomUUID().toString(),
        null);
  }
}

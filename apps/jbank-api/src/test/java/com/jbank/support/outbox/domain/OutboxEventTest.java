package com.jbank.support.outbox.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

  @Test
  void 생성_직후_상태는_PENDING이고_재시도횟수는_0이다() {
    OutboxEvent event = newEvent();

    assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    assertThat(event.getRetryCount()).isZero();
  }

  @Test
  void markPublished를_호출하면_PUBLISHED로_바뀌고_발행시각이_남는다() {
    OutboxEvent event = newEvent();
    OffsetDateTime publishedAt = OffsetDateTime.now();

    event.markPublished(publishedAt);

    assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    assertThat(event.getPublishedAt()).isEqualTo(publishedAt);
  }

  @Test
  void markFailed를_호출하면_PENDING을_유지한채_재시도횟수가_늘고_오류가_남는다() {
    OutboxEvent event = newEvent();

    event.markFailed("Kafka 연결 실패");

    assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    assertThat(event.getRetryCount()).isEqualTo(1);
    assertThat(event.getLastError()).isEqualTo("Kafka 연결 실패");
  }

  @Test
  void markFailed의_오류메시지가_500자를_넘으면_잘라서_저장한다() {
    OutboxEvent event = newEvent();
    String longError = "x".repeat(600);

    event.markFailed(longError);

    assertThat(event.getLastError()).hasSize(500);
  }

  private OutboxEvent newEvent() {
    return new OutboxEvent(
        "TRANSACTION", "1", "TRANSFER_COMPLETED", Map.of("amount", "1000"), OffsetDateTime.now());
  }
}

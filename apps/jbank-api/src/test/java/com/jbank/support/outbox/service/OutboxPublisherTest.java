package com.jbank.support.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jbank.support.outbox.domain.OutboxEvent;
import com.jbank.support.outbox.domain.OutboxEventStatus;
import com.jbank.support.outbox.repository.OutboxEventRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

  @Mock private OutboxEventRepository outboxEventRepository;
  @Mock private KafkaTemplate<String, String> kafkaTemplate;

  private OutboxPublisher outboxPublisher;

  @BeforeEach
  void setUp() {
    outboxPublisher =
        new OutboxPublisher(
            outboxEventRepository, kafkaTemplate, new ObjectMapper(), "jbank.events", 100);
  }

  @Test
  void 발행에_성공하면_PUBLISHED로_바뀌고_저장한다() {
    OutboxEvent event = newEvent();
    given(kafkaTemplate.send(eq("jbank.events"), eq("1"), anyString()))
        .willReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

    outboxPublisher.publishOne(event);

    assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    verify(outboxEventRepository).save(event);
  }

  @Test
  void 발행_메시지는_eventType을_포함한_envelope이다() throws Exception {
    OutboxEvent event = newEvent();
    given(kafkaTemplate.send(eq("jbank.events"), eq("1"), anyString()))
        .willReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

    outboxPublisher.publishOne(event);

    verify(kafkaTemplate).send(eq("jbank.events"), eq("1"), payloadCaptor.capture());
    JsonNode envelope = new ObjectMapper().readTree(payloadCaptor.getValue());
    assertThat(envelope.get("eventType").asText()).isEqualTo("TRANSFER_COMPLETED");
    assertThat(envelope.get("aggregateType").asText()).isEqualTo("TRANSACTION");
    assertThat(envelope.get("data").get("amount").asText()).isEqualTo("1000");
  }

  @Test
  void 발행에_실패하면_PENDING을_유지한채_재시도횟수가_늘고_저장한다() {
    OutboxEvent event = newEvent();
    CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
    failed.completeExceptionally(new RuntimeException("Kafka 연결 실패"));
    given(kafkaTemplate.send(eq("jbank.events"), eq("1"), anyString())).willReturn(failed);

    outboxPublisher.publishOne(event);

    assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    assertThat(event.getRetryCount()).isEqualTo(1);
    assertThat(event.getLastError()).isNotBlank();
    verify(outboxEventRepository).save(event);
  }

  private OutboxEvent newEvent() {
    return new OutboxEvent(
        "TRANSACTION", "1", "TRANSFER_COMPLETED", Map.of("amount", "1000"), OffsetDateTime.now());
  }
}

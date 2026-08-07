package com.jbank.support.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jbank.support.outbox.domain.OutboxEvent;
import com.jbank.support.outbox.domain.OutboxEventStatus;
import com.jbank.support.outbox.repository.OutboxEventRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발신함 폴링 발행기. PENDING 이벤트를 배치로 읽어 Kafka로 발행하고 상태를 갱신한다. 발행 실패는 이벤트를 PENDING으로 남겨 다음 주기에
 * 재시도한다(OutboxEvent.markFailed 참고) — 최소 한 번 전달을 보장하고, 중복 전달은 소비자 측 멱등 처리로 흡수하는 것을 전제로 한다.
 */
@Component
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  private static final long SEND_TIMEOUT_SECONDS = 5;

  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final String topic;
  private final int batchSize;

  public OutboxPublisher(
      OutboxEventRepository outboxEventRepository,
      KafkaTemplate<String, String> kafkaTemplate,
      ObjectMapper objectMapper,
      @Value("${jbank.outbox.topic}") String topic,
      @Value("${jbank.outbox.batch-size}") int batchSize) {
    this.outboxEventRepository = outboxEventRepository;
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
    this.topic = topic;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${jbank.outbox.poll-interval-ms}")
  public void publishPending() {
    Pageable page = PageRequest.of(0, batchSize);
    List<OutboxEvent> pending =
        outboxEventRepository.findByStatusOrderByOccurredAtAsc(OutboxEventStatus.PENDING, page);
    for (OutboxEvent event : pending) {
      publishOne(event);
    }
  }

  @Transactional
  void publishOne(OutboxEvent event) {
    try {
      String payload = objectMapper.writeValueAsString(event.getPayload());
      kafkaTemplate
          .send(topic, event.getAggregateId(), payload)
          .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      event.markPublished(OffsetDateTime.now());
    } catch (JsonProcessingException e) {
      log.error("발신함 이벤트 직렬화 실패: eventId={}", event.getEventId(), e);
      event.markFailed(e.getMessage());
    } catch (Exception e) {
      log.error("발신함 이벤트 발행 실패: eventId={}", event.getEventId(), e);
      event.markFailed(e.getMessage());
    }
    outboxEventRepository.save(event);
  }
}

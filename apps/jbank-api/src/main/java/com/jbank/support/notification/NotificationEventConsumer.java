package com.jbank.support.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 발신함이 발행한 이벤트를 구독해 알림을 로그로 남긴다. 실제 알림 발송(푸시·문자 등) 연동 전까지는 로그 출력으로 대체한다. */
@Component
public class NotificationEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

  private final ObjectMapper objectMapper;

  public NotificationEventConsumer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @KafkaListener(topics = "${jbank.outbox.topic}", groupId = "${spring.kafka.consumer.group-id}")
  public void onMessage(String message) throws Exception {
    JsonNode envelope = objectMapper.readTree(message);
    String eventType = envelope.path("eventType").asText();
    JsonNode data = envelope.path("data");

    switch (eventType) {
      case "TRANSFER_COMPLETED" ->
          log.info(
              "알림: 이체 완료 - transactionId={}, fromAccountId={}, toAccountId={}, amount={}",
              data.path("transactionId").asText(),
              data.path("fromAccountId").asText(),
              data.path("toAccountId").asText(),
              data.path("amount").asText());
      default -> log.debug("알림 소비자가 처리하지 않는 이벤트 타입: {}", eventType);
    }
  }
}

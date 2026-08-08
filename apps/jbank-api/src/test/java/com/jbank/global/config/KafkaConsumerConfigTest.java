package com.jbank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

class KafkaConsumerConfigTest {

  @SuppressWarnings("unchecked")
  @Test
  void 재시도를_소진하면_원본_토픽명에_DLT_접미사가_붙은_토픽으로_전송한다() {
    KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

    DefaultErrorHandler errorHandler =
        new KafkaConsumerConfig().kafkaErrorHandler(kafkaTemplate, 1, 0L);

    ConsumerRecord<String, String> record =
        new ConsumerRecord<>("jbank.events", 0, 5L, "key", "payload");

    errorHandler.handleRemaining(
        new RuntimeException("소비 실패"),
        List.of(record),
        mock(Consumer.class),
        mock(MessageListenerContainer.class));

    ArgumentCaptor<ProducerRecord<String, String>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(captor.capture());
    assertThat(captor.getValue().topic()).isEqualTo("jbank.events-dlt");
    assertThat(captor.getValue().value()).isEqualTo("payload");
  }
}

package com.jbank.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * 리스너에서 예외가 발생하면 고정 간격으로 재시도하고, 재시도를 모두 소진하면 원본 토픽명에 "-dlt"가 붙은 데드레터 토픽으로 원본 레코드를 전송한다. Spring
 * Boot가 이 빈을 자동으로 카프카 리스너 컨테이너 팩토리에 연결한다.
 */
@Configuration
public class KafkaConsumerConfig {

  @Bean
  public DefaultErrorHandler kafkaErrorHandler(
      KafkaTemplate<String, String> kafkaTemplate,
      @Value("${jbank.kafka.consumer.retry.max-attempts}") int maxAttempts,
      @Value("${jbank.kafka.consumer.retry.backoff-ms}") long backoffMs) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
    FixedBackOff backOff = new FixedBackOff(backoffMs, maxAttempts - 1L);
    return new DefaultErrorHandler(recoverer, backOff);
  }
}

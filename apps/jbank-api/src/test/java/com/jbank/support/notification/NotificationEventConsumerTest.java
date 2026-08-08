package com.jbank.support.notification;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class NotificationEventConsumerTest {

  private final NotificationEventConsumer consumer =
      new NotificationEventConsumer(new ObjectMapper());
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    appender = new ListAppender<>();
    appender.start();
    ((Logger) LoggerFactory.getLogger(NotificationEventConsumer.class)).addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    ((Logger) LoggerFactory.getLogger(NotificationEventConsumer.class)).detachAppender(appender);
  }

  @Test
  void 이체완료_이벤트를_받으면_알림_로그를_남긴다() throws Exception {
    String message =
        """
        {"eventType":"TRANSFER_COMPLETED","aggregateType":"TRANSACTION","aggregateId":"1",
         "data":{"transactionId":"1","fromAccountId":"10","toAccountId":"20","amount":"1000"}}
        """;

    consumer.onMessage(message);

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .contains("이체 완료")
        .contains("transactionId=1")
        .contains("fromAccountId=10")
        .contains("toAccountId=20")
        .contains("amount=1000");
  }

  @Test
  void 알수없는_이벤트타입은_무시한다() throws Exception {
    String message = """
        {"eventType":"UNKNOWN","data":{}}
        """;

    consumer.onMessage(message);

    assertThat(appender.list).isEmpty();
  }
}

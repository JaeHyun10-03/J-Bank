package com.jbank.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jbank.transfer.repository.PendingCreditRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** 한 계좌 반영이 계속 실패해도 다른 계좌는 반영되고, 같은 계좌의 오류 로그는 억제된다(BE-07, ADR 0012). */
class PendingCreditWorkerTest {

  private static final Long FAILING = 1L;
  private static final Long HEALTHY = 2L;

  private final PendingCreditRepository repository = mock(PendingCreditRepository.class);
  private final CreditApplier applier = mock(CreditApplier.class);
  private final PendingCreditWorker worker =
      new PendingCreditWorker(repository, applier, new SimpleMeterRegistry());
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void setUp() {
    when(repository.findAccountIdsWithUnapplied(100)).thenReturn(List.of(FAILING, HEALTHY));
    when(repository.unappliedStats()).thenReturn(List.<Object[]>of(new Object[] {1L, 0.5}));
    appender.start();
    ((Logger) LoggerFactory.getLogger(PendingCreditWorker.class)).addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    ((Logger) LoggerFactory.getLogger(PendingCreditWorker.class)).detachAppender(appender);
  }

  @Test
  void 같은_계좌가_연속으로_실패하면_오류_로그는_한_번만_남기고_다른_계좌는_계속_반영한다() {
    doThrow(new IllegalStateException("반영 실패")).when(applier).applyForAccount(FAILING);

    worker.run();
    worker.run();
    worker.run();

    assertThat(errorLogs()).isEqualTo(1);
    verify(applier, times(3)).applyForAccount(HEALTHY);
  }

  @Test
  void 한_번_성공한_뒤_다시_실패하면_다시_로그를_남긴다() {
    doThrow(new IllegalStateException("반영 실패"))
        .doReturn(1)
        .doThrow(new IllegalStateException("반영 실패"))
        .when(applier)
        .applyForAccount(FAILING);
    doReturn(1).when(applier).applyForAccount(HEALTHY);

    worker.run();
    worker.run();
    worker.run();

    assertThat(errorLogs()).isEqualTo(2);
  }

  private long errorLogs() {
    return appender.list.stream()
        .filter(
            e ->
                e.getLevel() == Level.ERROR
                    && e.getFormattedMessage().contains("accountId=" + FAILING))
        .count();
  }
}

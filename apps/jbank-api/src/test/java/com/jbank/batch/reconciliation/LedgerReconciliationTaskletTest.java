package com.jbank.batch.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jbank.account.repository.AccountRepository;
import com.jbank.ledger.domain.EntryType;
import com.jbank.ledger.repository.LedgerEntryRepository;
import com.jbank.transfer.repository.PendingCreditRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** 전역 차변/대변 비교는 아직 반영되지 않은 입금(차변만 기록된 금액)을 더해 판단한다(ADR 0012). */
class LedgerReconciliationTaskletTest {

  private final AccountRepository accounts = mock(AccountRepository.class);
  private final LedgerEntryRepository ledger = mock(LedgerEntryRepository.class);
  private final PendingCreditRepository pending = mock(PendingCreditRepository.class);
  private final LedgerReconciliationTasklet tasklet =
      new LedgerReconciliationTasklet(accounts, ledger, pending);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void setUp() {
    when(accounts.findAll()).thenReturn(List.of());
    when(ledger.sumBalanceByAccount()).thenReturn(List.of());
    appender.start();
    ((Logger) LoggerFactory.getLogger(LedgerReconciliationTasklet.class)).addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    ((Logger) LoggerFactory.getLogger(LedgerReconciliationTasklet.class)).detachAppender(appender);
  }

  @Test
  void 차변이_대변과_미반영_입금의_합과_같으면_불일치가_아니다() {
    givenTotals("1000.00", "700.00", "300.00");

    tasklet.execute(null, null);

    assertThat(appender.list).noneMatch(e -> e.getLevel() == Level.WARN);
  }

  @Test
  void 미반영_입금을_더해도_차이가_나면_불일치를_남긴다() {
    givenTotals("1000.00", "700.00", "0.00");

    tasklet.execute(null, null);

    assertThat(appender.list)
        .anyMatch(
            e ->
                e.getLevel() == Level.WARN
                    && e.getFormattedMessage().contains("전체 차변/대변 합 불일치")
                    && e.getFormattedMessage().contains("미반영입금=0.00"));
  }

  private void givenTotals(String debit, String credit, String unapplied) {
    when(ledger.sumAmountByEntryType(EntryType.DEBIT)).thenReturn(new BigDecimal(debit));
    when(ledger.sumAmountByEntryType(EntryType.CREDIT)).thenReturn(new BigDecimal(credit));
    when(pending.sumUnapplied()).thenReturn(new BigDecimal(unapplied));
  }
}

package com.jbank.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionStatus;
import com.jbank.transfer.repository.TransactionRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendingOtpExpirationSchedulerTest {

  @Mock private TransactionRepository transactionRepository;
  @Mock private PendingOtpCancellationService pendingOtpCancellationService;

  private PendingOtpExpirationScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler =
        new PendingOtpExpirationScheduler(transactionRepository, pendingOtpCancellationService, 3);
  }

  @Test
  void 조회된_만료_대기_거래를_모두_취소한다() {
    Transaction tx1 = mock(Transaction.class);
    Transaction tx2 = mock(Transaction.class);
    given(tx1.getTransactionId()).willReturn(1L);
    given(tx2.getTransactionId()).willReturn(2L);
    given(transactionRepository.findByStatusAndCreatedAtBefore(eq(TransactionStatus.PENDING_OTP), any()))
        .willReturn(List.of(tx1, tx2));

    scheduler.cancelExpired();

    verify(pendingOtpCancellationService).cancel(1L);
    verify(pendingOtpCancellationService).cancel(2L);
  }

  @Test
  void 대상이_없으면_아무것도_취소하지_않는다() {
    given(transactionRepository.findByStatusAndCreatedAtBefore(eq(TransactionStatus.PENDING_OTP), any()))
        .willReturn(List.of());

    scheduler.cancelExpired();

    verifyNoInteractions(pendingOtpCancellationService);
  }

  @Test
  void 컷오프는_현재_시각에서_만료시간만큼_뺀_시점이다() {
    given(transactionRepository.findByStatusAndCreatedAtBefore(eq(TransactionStatus.PENDING_OTP), any()))
        .willReturn(List.of());
    OffsetDateTime before = OffsetDateTime.now().minus(Duration.ofMinutes(3));

    scheduler.cancelExpired();

    ArgumentCaptor<OffsetDateTime> cutoffCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    verify(transactionRepository)
        .findByStatusAndCreatedAtBefore(eq(TransactionStatus.PENDING_OTP), cutoffCaptor.capture());
    OffsetDateTime after = OffsetDateTime.now().minus(Duration.ofMinutes(3));
    assertThat(cutoffCaptor.getValue()).isBetween(before, after);
  }
}

package com.jbank.transfer.service;

import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionStatus;
import com.jbank.transfer.repository.TransactionRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * OTP 유효시간이 지나도록 검증되지 않은 PENDING_OTP 거래를 정리한다(FR-AUTH-003). Redis의 OTP는 자체 TTL로
 * 사라지지만 거래 자체는 그대로 남으므로, 이 스케줄러가 지급정지를 풀고 거래를 취소한다.
 */
@Component
public class PendingOtpExpirationScheduler {

  private final TransactionRepository transactionRepository;
  private final PendingOtpCancellationService pendingOtpCancellationService;
  private final Duration expiry;

  public PendingOtpExpirationScheduler(
      TransactionRepository transactionRepository,
      PendingOtpCancellationService pendingOtpCancellationService,
      @Value("${jbank.transfer.otp.expiry-minutes:3}") long expiryMinutes) {
    this.transactionRepository = transactionRepository;
    this.pendingOtpCancellationService = pendingOtpCancellationService;
    this.expiry = Duration.ofMinutes(expiryMinutes);
  }

  @Scheduled(fixedDelayString = "${jbank.transfer.otp.cleanup.poll-interval-ms:60000}")
  public void cancelExpired() {
    OffsetDateTime cutoff = OffsetDateTime.now().minus(expiry);
    List<Transaction> expired =
        transactionRepository.findByStatusAndCreatedAtBefore(TransactionStatus.PENDING_OTP, cutoff);
    for (Transaction transaction : expired) {
      pendingOtpCancellationService.cancel(transaction.getTransactionId());
    }
  }
}

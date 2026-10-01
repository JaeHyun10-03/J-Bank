package com.jbank.transfer.service;

import com.jbank.transfer.repository.PendingCreditRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 미반영 입금이 있는 수신 계좌를 주기적으로 골라 {@link CreditApplier}로 반영한다(docs/adr/0012). 운영 api에서만 켠다 — 배치 JVM이 대사와
 * 동시에 잔액을 바꾸지 않게 batch 프로파일에서 끄고, 테스트는 반영 서비스를 직접 호출한다.
 */
@Component
@ConditionalOnProperty(name = "jbank.transfer.credit-worker.enabled", havingValue = "true")
public class PendingCreditWorker {

  private static final Logger log = LoggerFactory.getLogger(PendingCreditWorker.class);
  private static final int ACCOUNTS_PER_CYCLE = 100;
  private static final long FAILURE_LOG_INTERVAL_MS = 60_000;

  private final PendingCreditRepository pendingCreditRepository;
  private final CreditApplier creditApplier;
  // 게이지는 스크레이프 때 DB를 조회하지 않고 워커가 주기마다 계산한 값을 읽는다 — 커넥션 풀이
  // 고갈돼도 지표 응답이 막히지 않게.
  private final AtomicLong unappliedCount = new AtomicLong();
  private final AtomicLong oldestUnappliedMillis = new AtomicLong();
  private final Map<Long, Long> lastFailureLogAt = new ConcurrentHashMap<>();

  public PendingCreditWorker(
      PendingCreditRepository pendingCreditRepository,
      CreditApplier creditApplier,
      MeterRegistry meterRegistry) {
    this.pendingCreditRepository = pendingCreditRepository;
    this.creditApplier = creditApplier;
    Gauge.builder("jbank.credit.pending.count", unappliedCount, AtomicLong::get)
        .description("미반영 입금 대기 수")
        .register(meterRegistry);
    Gauge.builder(
            "jbank.credit.pending.oldest.seconds", oldestUnappliedMillis, v -> v.get() / 1000.0)
        .description("가장 오래된 미반영 입금 대기의 나이")
        .register(meterRegistry);
  }

  @Scheduled(fixedDelayString = "${jbank.transfer.credit-worker.poll-interval-ms:200}")
  public void run() {
    List<Long> accountIds = pendingCreditRepository.findAccountIdsWithUnapplied(ACCOUNTS_PER_CYCLE);
    for (Long accountId : accountIds) {
      try {
        creditApplier.applyForAccount(accountId);
        lastFailureLogAt.remove(accountId);
      } catch (RuntimeException e) {
        // 돈을 버릴 수 없으므로 다음 주기에 계속 재시도하되, 같은 계좌의 오류 로그는 1분에 한 번만 남긴다.
        // 오래 남은 미반영은 원장 대사가 경고한다.
        long now = System.currentTimeMillis();
        Long last = lastFailureLogAt.get(accountId);
        if (last == null || now - last >= FAILURE_LOG_INTERVAL_MS) {
          lastFailureLogAt.put(accountId, now);
          log.error("입금 반영 실패, 다음 주기에 재시도: accountId={}", accountId, e);
        }
      }
    }
    Object[] stats = pendingCreditRepository.unappliedStats().get(0);
    unappliedCount.set(((Number) stats[0]).longValue());
    oldestUnappliedMillis.set((long) (((Number) stats[1]).doubleValue() * 1000));
  }
}

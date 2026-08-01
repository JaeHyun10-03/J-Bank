package com.jbank.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbank.account.domain.Account;
import com.jbank.global.exception.ErrorCode;
import com.jbank.transfer.domain.TransactionException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * 시나리오①. 잔액 10만원 계좌에 1만원 출금 100건을 동시에 던지면 비관적 락이 요청을 직렬화하므로 정확히 10건만 성공하고 나머지 90건은 출금 가능 금액 초과로
 * 실패해야 한다.
 */
class ConcurrentWithdrawalScenarioTest extends AbstractConcurrencyTest {

  @Test
  void 잔액10만원에_1만원_출금_100건_동시요청하면_10건만_성공한다() throws InterruptedException {
    Account account = saveAccount(new BigDecimal("100000.00"));
    int requestCount = 100;
    ExecutorService executor = Executors.newFixedThreadPool(20);
    CountDownLatch ready = new CountDownLatch(requestCount);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger successCount = new AtomicInteger();
    AtomicInteger insufficientBalanceCount = new AtomicInteger();

    try {
      List<Future<?>> futures =
          IntStream.range(0, requestCount)
              .<Future<?>>mapToObj(
                  i ->
                      executor.submit(
                          () -> {
                            ready.countDown();
                            awaitUninterruptibly(start);
                            try {
                              withdrawalService.withdraw(
                                  account.getAccountId(),
                                  new BigDecimal("10000.00"),
                                  UUID.randomUUID().toString(),
                                  account.getCustomerId());
                              successCount.incrementAndGet();
                            } catch (TransactionException e) {
                              if (e.getErrorCode() == ErrorCode.TXN_001_INSUFFICIENT_BALANCE) {
                                insufficientBalanceCount.incrementAndGet();
                              } else {
                                throw e;
                              }
                            }
                          }))
              .toList();

      ready.await(10, TimeUnit.SECONDS);
      start.countDown();
      for (Future<?> future : futures) {
        try {
          future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
          throw new AssertionError("출금 요청 처리 중 예상치 못한 예외", e);
        }
      }
    } finally {
      executor.shutdown();
    }

    assertThat(successCount.get()).isEqualTo(10);
    assertThat(insufficientBalanceCount.get()).isEqualTo(90);
    Account updated = accountRepository.findById(account.getAccountId()).orElseThrow();
    assertThat(updated.getCurrentBalanceCache()).isEqualByComparingTo("0.00");
    assertThat(ledgerEntryRepository.findByAccountId(account.getAccountId())).hasSize(10);
  }

  private static void awaitUninterruptibly(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }
}

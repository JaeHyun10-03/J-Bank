package com.jbank.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbank.account.domain.Account;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 시나리오②. A→B, B→A 이체를 각 50건씩 동시에 던진다. 두 계좌번호를 오름차순 정렬한 순서로 락을 잡아 방향에 관계없이 항상 같은 계좌를 먼저 잠그므로 교착상태 없이
 * 전부 처리되어야 하고, 두 계좌 잔액의 합은 이체 전후로 보존되어야 한다.
 */
class ConcurrentBidirectionalTransferScenarioTest extends AbstractConcurrencyTest {

  @Test
  void 양방향_이체_50건씩_동시요청해도_교착상태_없이_전부_처리되고_잔액합이_보존된다() throws InterruptedException {
    Account a = saveAccount(new BigDecimal("1000000.00"));
    Account b = saveAccount(new BigDecimal("1000000.00"));
    BigDecimal totalBefore = a.getCurrentBalanceCache().add(b.getCurrentBalanceCache());
    int perDirection = 50;
    ExecutorService executor = Executors.newFixedThreadPool(20);
    CountDownLatch ready = new CountDownLatch(perDirection * 2);
    CountDownLatch start = new CountDownLatch(1);

    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < perDirection; i++) {
        futures.add(
            submitTransfer(executor, ready, start, a.getAccountNumber(), b.getAccountNumber()));
        futures.add(
            submitTransfer(executor, ready, start, b.getAccountNumber(), a.getAccountNumber()));
      }

      ready.await(10, TimeUnit.SECONDS);
      start.countDown();
      for (Future<?> future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }
    } catch (Exception e) {
      throw new AssertionError("교착상태 또는 예상치 못한 실패로 이체가 완료되지 않았다", e);
    } finally {
      executor.shutdown();
    }

    Account updatedA = accountRepository.findById(a.getAccountId()).orElseThrow();
    Account updatedB = accountRepository.findById(b.getAccountId()).orElseThrow();
    BigDecimal totalAfter =
        updatedA.getCurrentBalanceCache().add(updatedB.getCurrentBalanceCache());
    assertThat(totalAfter).isEqualByComparingTo(totalBefore);
    assertThat(ledgerEntryRepository.findByAccountId(a.getAccountId())).hasSize(perDirection * 2);
    assertThat(ledgerEntryRepository.findByAccountId(b.getAccountId())).hasSize(perDirection * 2);
  }

  private Future<?> submitTransfer(
      ExecutorService executor,
      CountDownLatch ready,
      CountDownLatch start,
      String fromAccountNumber,
      String toAccountNumber) {
    return executor.submit(
        () -> {
          ready.countDown();
          awaitUninterruptibly(start);
          transferService.transfer(
              fromAccountNumber,
              toAccountNumber,
              new BigDecimal("1000.00"),
              UUID.randomUUID().toString(),
              null);
        });
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

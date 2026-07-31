package com.jbank.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbank.account.domain.Account;
import com.jbank.transfer.dto.TransferResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * 시나리오③. 동일한 멱등성 키로 이체 요청 10건을 동시에 던지면, 사전 조회를 모두 통과한 요청들이 DB 유니크 제약(idempotency_key)에서 걸러져 거래가 정확히
 * 한 건만 생성되고 10개의 응답이 전부 같은 거래식별자를 반환해야 한다.
 */
class ConcurrentIdempotencyKeyScenarioTest extends AbstractConcurrencyTest {

  @Test
  void 동일한_멱등성_키_10건_동시요청은_거래를_한_건만_생성한다() throws InterruptedException {
    Account from = saveAccount(new BigDecimal("1000000.00"));
    Account to = saveAccount(new BigDecimal("0.00"));
    String idempotencyKey = "shared-idempotency-key";
    int requestCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(requestCount);
    CountDownLatch ready = new CountDownLatch(requestCount);
    CountDownLatch start = new CountDownLatch(1);

    List<TransferResponse> responses;
    try {
      List<Future<TransferResponse>> futures =
          IntStream.range(0, requestCount)
              .<Callable<TransferResponse>>mapToObj(
                  i ->
                      () -> {
                        ready.countDown();
                        start.await();
                        return transferService.transfer(
                            from.getAccountNumber(),
                            to.getAccountNumber(),
                            new BigDecimal("10000.00"),
                            idempotencyKey,
                            null);
                      })
              .map(executor::submit)
              .toList();

      ready.await(10, TimeUnit.SECONDS);
      start.countDown();
      responses =
          futures.stream()
              .map(
                  f -> {
                    try {
                      return f.get(30, TimeUnit.SECONDS);
                    } catch (Exception e) {
                      throw new AssertionError("멱등 이체 요청 처리 실패", e);
                    }
                  })
              .toList();
    } finally {
      executor.shutdown();
    }

    Set<String> distinctTransactionIds =
        responses.stream().map(TransferResponse::transactionId).collect(Collectors.toSet());
    assertThat(distinctTransactionIds).hasSize(1);
    assertThat(transactionRepository.findByIdempotencyKey(idempotencyKey)).isPresent();
    Account updatedFrom = accountRepository.findById(from.getAccountId()).orElseThrow();
    assertThat(updatedFrom.getCurrentBalanceCache()).isEqualByComparingTo("990000.00");
    assertThat(ledgerEntryRepository.findByAccountId(from.getAccountId())).hasSize(1);
  }
}

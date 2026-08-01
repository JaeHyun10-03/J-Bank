package com.jbank.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbank.account.domain.Account;
import com.jbank.ledger.domain.EntryType;
import com.jbank.ledger.domain.LedgerEntry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * 시나리오④. 랜덤 계좌쌍에 대해 이체 1000건을 동시에 실행한 뒤, 모든 계좌에서 원장 엔트리 합산과 캐시 잔액이 일치하고, 전체 차변 합과 대변 합이 같은지 확인한다.
 * 계좌당 초기 잔액을 이체 총량보다 훨씬 크게 잡아 잔액 부족으로 인한 실패 없이 1000건 전부 성공하게 한다.
 */
class ConcurrentLedgerReconciliationScenarioTest extends AbstractConcurrencyTest {

  @Test
  void 랜덤_계좌쌍_이체_1000건_후_원장합산과_캐시잔액이_계좌마다_일치하고_전체_차대변이_같다() throws InterruptedException {
    int accountCount = 20;
    int transferCount = 1000;
    List<Account> accounts =
        IntStream.range(0, accountCount)
            .mapToObj(i -> saveAccount(new BigDecimal("10000000.00")))
            .toList();
    Random random = new Random(42);

    ExecutorService executor = Executors.newFixedThreadPool(20);
    try {
      List<Future<?>> futures =
          IntStream.range(0, transferCount)
              .<Future<?>>mapToObj(
                  i -> {
                    int fromIdx = random.nextInt(accountCount);
                    int toIdx;
                    do {
                      toIdx = random.nextInt(accountCount);
                    } while (toIdx == fromIdx);
                    String fromAccountNumber = accounts.get(fromIdx).getAccountNumber();
                    String toAccountNumber = accounts.get(toIdx).getAccountNumber();
                    Long fromCustomerId = accounts.get(fromIdx).getCustomerId();
                    return executor.submit(
                        () ->
                            transferService.transfer(
                                fromAccountNumber,
                                toAccountNumber,
                                new BigDecimal("100.00"),
                                UUID.randomUUID().toString(),
                                null,
                                fromCustomerId));
                  })
              .toList();

      for (Future<?> future : futures) {
        future.get(60, TimeUnit.SECONDS);
      }
    } catch (Exception e) {
      throw new AssertionError("이체 1000건 처리 중 예상치 못한 실패", e);
    } finally {
      executor.shutdown();
    }

    BigDecimal totalDebit = BigDecimal.ZERO;
    BigDecimal totalCredit = BigDecimal.ZERO;
    for (Account seed : accounts) {
      Account account = accountRepository.findById(seed.getAccountId()).orElseThrow();
      List<LedgerEntry> entries = ledgerEntryRepository.findByAccountId(account.getAccountId());
      BigDecimal ledgerSum =
          entries.stream()
              .map(
                  entry ->
                      entry.getEntryType() == EntryType.CREDIT
                          ? entry.getAmount()
                          : entry.getAmount().negate())
              .reduce(BigDecimal.ZERO, BigDecimal::add)
              .add(new BigDecimal("10000000.00"));

      assertThat(account.getCurrentBalanceCache()).isEqualByComparingTo(ledgerSum);

      for (LedgerEntry entry : entries) {
        if (entry.getEntryType() == EntryType.DEBIT) {
          totalDebit = totalDebit.add(entry.getAmount());
        } else {
          totalCredit = totalCredit.add(entry.getAmount());
        }
      }
    }

    assertThat(totalDebit).isEqualByComparingTo(totalCredit);
    assertThat(totalDebit).isEqualByComparingTo(new BigDecimal("100000.00"));
  }
}

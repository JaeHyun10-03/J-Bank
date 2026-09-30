package com.jbank.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbank.account.domain.Account;
import com.jbank.ledger.domain.EntryType;
import com.jbank.ledger.domain.LedgerEntry;
import com.jbank.transfer.domain.PendingCredit;
import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionType;
import com.jbank.transfer.service.PendingCreditWorker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 입금 대기 반영(ADR 0012). 이체 전환 전이라 대기 행을 테스트가 직접 넣는다. 반영은 수신 계좌 행을 한 번 잠그고 거래마다 대변 원장 1건(누적 스냅샷)을 남기며,
 * 같은 대기는 몇 번을 동시에 불러도 한 번만 반영돼야 한다.
 */
class PendingCreditApplyScenarioTest extends AbstractConcurrencyTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void 미반영_입금을_모아_잔액과_대변_원장에_한번씩_반영한다() {
    Account from = saveAccount(new BigDecimal("100000.00"));
    Account to = saveAccount(new BigDecimal("1000.00"));
    savePending(from, to, "300.00");
    savePending(from, to, "200.00");

    int applied = creditApplier.applyForAccount(to.getAccountId());

    assertThat(applied).isEqualTo(2);
    Account updated = accountRepository.findById(to.getAccountId()).orElseThrow();
    assertThat(updated.getCurrentBalanceCache()).isEqualByComparingTo("1500.00");
    List<LedgerEntry> credits =
        ledgerEntryRepository.findByAccountId(to.getAccountId()).stream()
            .sorted(Comparator.comparing(LedgerEntry::getEntryId))
            .toList();
    assertThat(credits).extracting(LedgerEntry::getEntryType).containsOnly(EntryType.CREDIT);
    assertThat(credits)
        .extracting(LedgerEntry::getBalanceAfterSnapshot)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("1300.00"), new BigDecimal("1500.00"));
    assertThat(pendingCreditRepository.findAll())
        .filteredOn(p -> p.getAccountId().equals(to.getAccountId()))
        .allSatisfy(p -> assertThat(p.getAppliedAt()).isNotNull());
    assertThat(creditApplier.applyForAccount(to.getAccountId())).isZero();
  }

  @Test
  void 같은_계좌를_동시에_반영해도_한_번만_반영된다() throws Exception {
    Account from = saveAccount(new BigDecimal("1000000.00"));
    Account to = saveAccount(BigDecimal.ZERO);
    int count = 50;
    for (int i = 0; i < count; i++) {
      savePending(from, to, "10.00");
    }

    ExecutorService executor = Executors.newFixedThreadPool(4);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Integer>> futures =
          java.util.stream.IntStream.range(0, 4)
              .mapToObj(
                  i ->
                      executor.submit(
                          (Callable<Integer>)
                              () -> {
                                start.await();
                                return creditApplier.applyForAccount(to.getAccountId());
                              }))
              .toList();
      start.countDown();
      int total = 0;
      for (Future<Integer> future : futures) {
        total += future.get(30, TimeUnit.SECONDS);
      }
      assertThat(total).isEqualTo(count);
    } finally {
      executor.shutdownNow();
    }

    assertThat(accountRepository.findById(to.getAccountId()).orElseThrow().getCurrentBalanceCache())
        .isEqualByComparingTo("500.00");
    assertThat(ledgerEntryRepository.findByAccountId(to.getAccountId())).hasSize(count);
  }

  @Test
  void 한_계좌_반영이_실패해도_다른_계좌는_반영되고_실패한_계좌는_나중에_다시_반영된다() {
    Account from = saveAccount(new BigDecimal("100000.00"));
    Account broken = saveAccount(BigDecimal.ZERO);
    Account healthy = saveAccount(BigDecimal.ZERO);
    savePending(from, broken, "100.00");
    savePending(from, healthy, "100.00");
    // 운영 코드에 장애 훅을 두지 않고, 테스트 전용 트리거로 그 계좌의 잔액 갱신만 실패시킨다.
    jdbcTemplate.execute(
        "create or replace function fail_credit() returns trigger as $$ begin "
            + "raise exception 'injected failure'; end $$ language plpgsql");
    jdbcTemplate.execute(
        "create trigger fail_credit_trg before update on accounts for each row when (new.account_id = "
            + broken.getAccountId()
            + ") execute function fail_credit()");
    try {
      assertThatThrownBy(() -> creditApplier.applyForAccount(broken.getAccountId()))
          .isInstanceOf(RuntimeException.class);
      assertThat(creditApplier.applyForAccount(healthy.getAccountId())).isEqualTo(1);
      assertThat(unappliedCount(broken)).isEqualTo(1);
      assertThat(ledgerEntryRepository.findByAccountId(broken.getAccountId())).isEmpty();
    } finally {
      jdbcTemplate.execute("drop trigger fail_credit_trg on accounts");
    }

    assertThat(creditApplier.applyForAccount(broken.getAccountId())).isEqualTo(1);
    assertThat(unappliedCount(broken)).isZero();
  }

  @Test
  void 한_번에_반영하지_못한_대기는_다음_호출이_이어서_반영한다() {
    Account from = saveAccount(new BigDecimal("10000000.00"));
    Account to = saveAccount(BigDecimal.ZERO);
    int count = 520; // 한 번에 500건까지
    for (int i = 0; i < count; i++) {
      savePending(from, to, "1.00");
    }

    assertThat(creditApplier.applyForAccount(to.getAccountId())).isEqualTo(500);
    assertThat(creditApplier.applyForAccount(to.getAccountId())).isEqualTo(20);
    assertThat(accountRepository.findById(to.getAccountId()).orElseThrow().getCurrentBalanceCache())
        .isEqualByComparingTo("520.00");
  }

  @Test
  void 해지된_계좌의_대기는_반영하지_않는다() {
    Account from = saveAccount(new BigDecimal("100000.00"));
    Account to = saveAccount(BigDecimal.ZERO);
    savePending(from, to, "100.00");
    Account closed = accountRepository.findById(to.getAccountId()).orElseThrow();
    closed.close();
    accountRepository.saveAndFlush(closed);

    assertThat(creditApplier.applyForAccount(to.getAccountId())).isZero();
    assertThat(unappliedCount(to)).isEqualTo(1);
    // 다른 테스트의 반영 대상에 남지 않게 치운다.
    jdbcTemplate.update("delete from pending_credits where account_id = ?", to.getAccountId());
  }

  @Test
  void 워커는_미반영_계좌를_반영하고_지표를_갱신한다() {
    Account from = saveAccount(new BigDecimal("100000.00"));
    Account to = saveAccount(BigDecimal.ZERO);
    savePending(from, to, "100.00");
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PendingCreditWorker worker =
        new PendingCreditWorker(pendingCreditRepository, creditApplier, registry);

    assertThat(registry.get("jbank.credit.pending.count").gauge().value()).isZero();

    worker.run();

    assertThat(unappliedCount(to)).isZero();
    assertThat(registry.get("jbank.credit.pending.count").gauge().value())
        .isEqualTo(
            pendingCreditRepository.findAll().stream()
                .filter(p -> p.getAppliedAt() == null)
                .count());
  }

  private void savePending(Account from, Account to, String amount) {
    Transaction transaction =
        transactionRepository.saveAndFlush(
            new Transaction(
                TransactionType.TRANSFER,
                from.getAccountId(),
                to.getAccountId(),
                new BigDecimal(amount),
                UUID.randomUUID().toString(),
                null));
    pendingCreditRepository.saveAndFlush(
        new PendingCredit(
            transaction.getTransactionId(), to.getAccountId(), new BigDecimal(amount)));
  }

  private long unappliedCount(Account account) {
    return pendingCreditRepository.findAll().stream()
        .filter(p -> p.getAccountId().equals(account.getAccountId()))
        .filter(p -> p.getAppliedAt() == null)
        .count();
  }
}

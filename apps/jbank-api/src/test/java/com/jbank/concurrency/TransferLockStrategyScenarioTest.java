package com.jbank.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.jbank.account.domain.Account;
import com.jbank.transfer.domain.TransactionStatus;
import com.jbank.transfer.service.TransferLockStrategy;
import com.jbank.transfer.service.TransferLockStrategy.Mode;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이체 락 전략별 동시 양방향 이체(교착 재현 실험, TransferLockStrategy). 하위 클래스가 전략 하나씩을 설정한다. OTP 임계금액을 낮춰 1천 원은 즉시
 * 이체, 1만 원은 인증 대기 이체가 되게 한다.
 */
@TestPropertySource(properties = "jbank.transfer.otp.threshold-amount=5000")
abstract class TransferLockStrategyScenarioTest extends AbstractConcurrencyTest {

  private static final String DEADLOCK = "40P01";
  private static final String LOCK_NOT_AVAILABLE = "55P03";
  private static final BigDecimal IMMEDIATE_AMOUNT = new BigDecimal("1000.00");
  private static final BigDecimal OTP_AMOUNT = new BigDecimal("10000.00");
  private static final int PER_DIRECTION = 50;
  private static final int THREADS = 20;
  private static final int MAX_ROUNDS = 5;

  @Autowired private TransferLockStrategy lockStrategy;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;

  abstract Mode expectedMode();

  private boolean deadlockExpected() {
    return expectedMode() == Mode.UNSORTED_FOR_UPDATE;
  }

  @Test
  void 설정한_전략으로_떠_있다() {
    assertThat(lockStrategy.mode()).isEqualTo(expectedMode());
  }

  @Test
  void 즉시_이체_양방향_동시() throws InterruptedException {
    for (int round = 1; round <= (deadlockExpected() ? MAX_ROUNDS : 1); round++) {
      Account a = saveAccount(new BigDecimal("10000000.00"));
      Account b = saveAccount(new BigDecimal("10000000.00"));
      List<Runnable> tasks = new ArrayList<>();
      for (int i = 0; i < PER_DIRECTION; i++) {
        tasks.add(() -> transfer(a, b, IMMEDIATE_AMOUNT));
        tasks.add(() -> transfer(b, a, IMMEDIATE_AMOUNT));
      }

      Outcome outcome = runConcurrently(tasks);
      report("즉시 이체", round, outcome);

      assertThat(outcome.others).as("40P01 외 예외").isEmpty();
      applyAllPendingCredits();
      assertBalancesPreserved(a, b);
      // 교착으로 실패한 이체는 거래·원장·입금 대기를 하나도 남기지 않는다.
      assertThat(countTransactions(a, b, TransactionStatus.COMPLETED)).isEqualTo(outcome.success);
      assertThat(countAllTransactions(a, b)).isEqualTo(outcome.success);
      assertThat(countLedgerEntries(a, b)).isEqualTo(outcome.success * 2L);
      assertThat(countPendingCredits(a, b)).isEqualTo(outcome.success);

      if (!deadlockExpected()) {
        assertThat(outcome.deadlocks).isZero();
        assertThat(outcome.success).isEqualTo(PER_DIRECTION * 2);
        return;
      }
      if (outcome.deadlocks > 0) {
        return;
      }
    }
    fail(MAX_ROUNDS + "회 반복에서 교착이 한 번도 나지 않았다");
  }

  @Test
  void OTP_완료_양방향_동시() throws InterruptedException {
    for (int round = 1; round <= (deadlockExpected() ? MAX_ROUNDS : 1); round++) {
      Account a = saveAccount(new BigDecimal("10000000.00"));
      Account b = saveAccount(new BigDecimal("10000000.00"));
      List<Long> pending = new ArrayList<>();
      for (int i = 0; i < PER_DIRECTION; i++) {
        pending.add(transfer(a, b, OTP_AMOUNT));
        pending.add(transfer(b, a, OTP_AMOUNT));
      }
      List<Runnable> tasks = new ArrayList<>();
      for (Long transactionId : pending) {
        tasks.add(() -> transferService.completeAfterOtp(transactionId));
      }

      Outcome outcome = runConcurrently(tasks);
      report("OTP 완료", round, outcome);

      assertThat(outcome.others).as("40P01 외 예외").isEmpty();
      // 교착으로 실패한 완료는 인증 대기로 남고 지급정지도 그대로다.
      assertThat(countTransactions(a, b, TransactionStatus.COMPLETED)).isEqualTo(outcome.success);
      long stillPending = countTransactions(a, b, TransactionStatus.PENDING_OTP);
      assertThat(stillPending).isEqualTo(PER_DIRECTION * 2L - outcome.success);
      assertThat(holdTotal(a, b))
          .isEqualByComparingTo(OTP_AMOUNT.multiply(BigDecimal.valueOf(stillPending)));
      applyAllPendingCredits();
      assertBalancesPreserved(a, b);
      assertThat(countLedgerEntries(a, b)).isEqualTo(outcome.success * 2L);

      if (!deadlockExpected()) {
        assertThat(outcome.deadlocks).isZero();
        assertThat(outcome.success).isEqualTo(PER_DIRECTION * 2);
        return;
      }
      if (outcome.deadlocks > 0) {
        return;
      }
    }
    fail(MAX_ROUNDS + "회 반복에서 교착이 한 번도 나지 않았다");
  }

  @Test
  void 즉시_이체가_쥔_락_모드() throws Exception {
    Account a = saveAccount(new BigDecimal("10000000.00"));
    Account b = saveAccount(new BigDecimal("10000000.00"));
    assertLockModeWhileHeld(() -> transfer(a, b, IMMEDIATE_AMOUNT), a, b);
  }

  @Test
  void OTP_완료가_쥔_락_모드() throws Exception {
    Account a = saveAccount(new BigDecimal("10000000.00"));
    Account b = saveAccount(new BigDecimal("10000000.00"));
    Long transactionId = transfer(a, b, OTP_AMOUNT);
    assertLockModeWhileHeld(() -> transferService.completeAfterOtp(transactionId), a, b);
  }

  /**
   * 이체가 끝나기 전(커밋 전) 다른 세션이 두 계좌를 FOR KEY SHARE NOWAIT로 읽어 본다. for-update 전략은 둘 다 막히고(55P03),
   * current는 송금 NO KEY UPDATE·수신 KEY SHARE라 둘 다 읽힌다.
   */
  private void assertLockModeWhileHeld(Runnable action, Account sender, Account receiver)
      throws Exception {
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService holder = Executors.newSingleThreadExecutor();
    try {
      Future<?> held =
          holder.submit(
              () ->
                  new TransactionTemplate(transactionManager)
                      .executeWithoutResult(
                          status -> {
                            action.run();
                            locked.countDown();
                            awaitUninterruptibly(release);
                            status.setRollbackOnly();
                          }));
      assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
      String expected = expectedMode() == Mode.CURRENT ? null : LOCK_NOT_AVAILABLE;
      try {
        assertThat(keyShareNowait(sender)).as("송금 계좌").isEqualTo(expected);
        assertThat(keyShareNowait(receiver)).as("수신 계좌").isEqualTo(expected);
      } finally {
        release.countDown();
      }
      held.get(10, TimeUnit.SECONDS);
    } finally {
      holder.shutdownNow();
    }
  }

  /** 읽히면 null, 막히면 SQLState. */
  private String keyShareNowait(Account account) {
    try {
      jdbcTemplate.queryForList(
          "select account_id from accounts where account_id = ? for key share nowait",
          account.getAccountId());
      return null;
    } catch (RuntimeException e) {
      return sqlState(e);
    }
  }

  private Long transfer(Account from, Account to, BigDecimal amount) {
    return Long.valueOf(
        transferService
            .transfer(
                from.getAccountNumber(),
                to.getAccountNumber(),
                amount,
                UUID.randomUUID().toString(),
                null,
                from.getCustomerId())
            .transactionId());
  }

  private Outcome runConcurrently(List<Runnable> tasks) throws InterruptedException {
    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    CountDownLatch ready = new CountDownLatch(tasks.size());
    CountDownLatch start = new CountDownLatch(1);
    Outcome outcome = new Outcome();
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (Runnable task : tasks) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  awaitUninterruptibly(start);
                  task.run();
                }));
      }
      ready.await(10, TimeUnit.SECONDS);
      start.countDown();
      for (Future<?> future : futures) {
        try {
          future.get(60, TimeUnit.SECONDS);
          outcome.success++;
        } catch (ExecutionException e) {
          if (DEADLOCK.equals(sqlState(e.getCause()))) {
            outcome.deadlocks++;
          } else {
            outcome.others.add(e.getCause());
          }
        } catch (Exception e) {
          outcome.others.add(e);
        }
      }
    } finally {
      executor.shutdownNow();
    }
    return outcome;
  }

  private void report(String path, int round, Outcome outcome) {
    System.out.printf(
        "[lock-strategy=%s] %s %d회차: 성공 %d, 교착(40P01) %d, 기타 %d%n",
        expectedMode().value(),
        path,
        round,
        outcome.success,
        outcome.deadlocks,
        outcome.others.size());
  }

  private void assertBalancesPreserved(Account a, Account b) {
    Account updatedA = accountRepository.findById(a.getAccountId()).orElseThrow();
    Account updatedB = accountRepository.findById(b.getAccountId()).orElseThrow();
    assertThat(updatedA.getCurrentBalanceCache().add(updatedB.getCurrentBalanceCache()))
        .isEqualByComparingTo(a.getCurrentBalanceCache().add(b.getCurrentBalanceCache()));
    assertThat(updatedA.getCurrentBalanceCache()).isNotNegative();
    assertThat(updatedB.getCurrentBalanceCache()).isNotNegative();
  }

  private BigDecimal holdTotal(Account a, Account b) {
    return accountRepository
        .findById(a.getAccountId())
        .orElseThrow()
        .getHoldAmount()
        .add(accountRepository.findById(b.getAccountId()).orElseThrow().getHoldAmount());
  }

  private long countTransactions(Account a, Account b, TransactionStatus status) {
    return jdbcTemplate.queryForObject(
        "select count(*) from transactions where from_account_id in (?, ?) and status = ?",
        Long.class,
        a.getAccountId(),
        b.getAccountId(),
        status.name());
  }

  private long countAllTransactions(Account a, Account b) {
    return jdbcTemplate.queryForObject(
        "select count(*) from transactions where from_account_id in (?, ?)",
        Long.class,
        a.getAccountId(),
        b.getAccountId());
  }

  private long countLedgerEntries(Account a, Account b) {
    return jdbcTemplate.queryForObject(
        "select count(*) from ledger_entries where account_id in (?, ?)",
        Long.class,
        a.getAccountId(),
        b.getAccountId());
  }

  private long countPendingCredits(Account a, Account b) {
    return jdbcTemplate.queryForObject(
        "select count(*) from pending_credits where account_id in (?, ?)",
        Long.class,
        a.getAccountId(),
        b.getAccountId());
  }

  /** 예외 원인 사슬에서 처음 만나는 SQLException의 SQLState. */
  private static String sqlState(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof SQLException sql && sql.getSQLState() != null) {
        return sql.getSQLState();
      }
    }
    return null;
  }

  private static void awaitUninterruptibly(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  private static final class Outcome {
    int success;
    int deadlocks;
    final List<Throwable> others = new ArrayList<>();
  }
}

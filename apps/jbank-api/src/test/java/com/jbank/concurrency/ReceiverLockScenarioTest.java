package com.jbank.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountException;
import com.jbank.account.domain.AccountNumberGenerator;
import com.jbank.account.domain.AccountStatus;
import com.jbank.account.service.AccountService;
import com.jbank.global.exception.DomainException;
import com.jbank.global.exception.ErrorCode;
import com.jbank.ledger.domain.EntryType;
import com.jbank.transfer.domain.TransactionStatus;
import com.jbank.transfer.dto.TransferResponse;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이체는 수신 계좌를 KEY SHARE로만 읽는다(ADR 0012). 반영 워커·다른 이체의 NO KEY UPDATE에는 막히지 않고, 해지의 FOR UPDATE와는 순서가
 * 정해져 해지된 계좌에 입금이 남지 않아야 한다.
 */
@Import({AccountService.class, AccountNumberGenerator.class})
class ReceiverLockScenarioTest extends AbstractConcurrencyTest {

  private static final BigDecimal OTP_AMOUNT = new BigDecimal("15000000.00");

  @Autowired private AccountService accountService;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void 반영_워커가_수신_계좌를_잡고_있어도_이체는_기다리지_않는다() throws Exception {
    Account from = saveAccount(new BigDecimal("100000.00"));
    Account to = saveAccount(BigDecimal.ZERO);

    long elapsed =
        whileLocked(
            "select 1 from accounts where account_id = ? for no key update",
            to,
            3000,
            () -> transfer(from, to, "1000.00"));

    assertThat(elapsed).isLessThan(2000);
    applyAllPendingCredits();
    assertThat(balance(to)).isEqualByComparingTo("1000.00");
  }

  @Test
  void 해지가_수신_계좌를_잠근_동안에는_이체가_기다린다() throws Exception {
    Account from = saveAccount(new BigDecimal("100000.00"));
    Account to = saveAccount(BigDecimal.ZERO);

    long elapsed =
        whileLocked(
            "select 1 from accounts where account_id = ? for update",
            to,
            1000,
            () -> transfer(from, to, "1000.00"));

    assertThat(elapsed).isGreaterThanOrEqualTo(800);
  }

  @Test
  void 이체와_해지가_동시에_와도_해지된_계좌에_입금이_남지_않는다() throws Exception {
    for (int i = 0; i < 20; i++) {
      Account from = saveAccount(new BigDecimal("100000.00"));
      Account to = saveAccount(BigDecimal.ZERO);
      ExecutorService executor = Executors.newFixedThreadPool(2);
      CountDownLatch start = new CountDownLatch(1);
      try {
        Future<?> transfer =
            executor.submit(
                () -> {
                  start.await();
                  return transfer(from, to, "1000.00");
                });
        Future<?> close =
            executor.submit(
                () -> {
                  start.await();
                  return accountService.close(to.getAccountId(), to.getCustomerId());
                });
        start.countDown();
        boolean transferred = succeeded(transfer);
        boolean closed = succeeded(close);

        // 둘 중 하나만 성공한다: 이체가 먼저면 해지는 ACC_012, 해지가 먼저면 이체는 TXN_004.
        assertThat(transferred ^ closed).isTrue();
        Account after = accountRepository.findById(to.getAccountId()).orElseThrow();
        if (after.getStatus() == AccountStatus.CLOSED) {
          assertThat(unapplied(to)).isZero();
        }
      } finally {
        executor.shutdownNow();
      }
      applyAllPendingCredits();
    }
  }

  @Test
  void 인증_대기_중에는_받는_계좌_해지가_거절되고_인증_성공_뒤_정상_반영된다() {
    Account from = saveAccount(new BigDecimal("20000000.00"));
    Account to = saveAccount(BigDecimal.ZERO);
    TransferResponse pending =
        transferService.transfer(
            from.getAccountNumber(),
            to.getAccountNumber(),
            OTP_AMOUNT,
            UUID.randomUUID().toString(),
            null,
            from.getCustomerId());
    assertThat(pending.status()).isEqualTo(TransactionStatus.PENDING_OTP);

    assertThatThrownBy(() -> accountService.close(to.getAccountId(), to.getCustomerId()))
        .isInstanceOf(AccountException.class)
        .satisfies(
            ex ->
                assertThat(((AccountException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACC_012_PENDING_CREDIT_EXISTS));

    transferService.completeAfterOtp(Long.valueOf(pending.transactionId()));
    applyAllPendingCredits();

    assertThat(balance(to)).isEqualByComparingTo(OTP_AMOUNT);
    assertThat(accountRepository.findById(to.getAccountId()).orElseThrow().getStatus())
        .isEqualTo(AccountStatus.ACTIVE);
  }

  @Test
  void 반영과_해지가_동시에_와도_잔액과_원장_합이_같다() throws Exception {
    for (int i = 0; i < 20; i++) {
      Account from = saveAccount(new BigDecimal("100000.00"));
      Account to = saveAccount(BigDecimal.ZERO);
      transfer(from, to, "1000.00");
      ExecutorService executor = Executors.newFixedThreadPool(2);
      CountDownLatch start = new CountDownLatch(1);
      try {
        Future<?> apply =
            executor.submit(
                () -> {
                  start.await();
                  return creditApplier.applyForAccount(to.getAccountId());
                });
        Future<?> close =
            executor.submit(
                () -> {
                  start.await();
                  return accountService.close(to.getAccountId(), to.getCustomerId());
                });
        start.countDown();
        apply.get(30, TimeUnit.SECONDS);
        // 반영 전이면 ACC_012, 반영 후면 ACC_008로 거절된다. 어느 쪽이든 해지되면 안 된다.
        assertThat(succeeded(close)).isFalse();
      } finally {
        executor.shutdownNow();
      }
      Account after = accountRepository.findById(to.getAccountId()).orElseThrow();
      assertThat(after.getStatus()).isEqualTo(AccountStatus.ACTIVE);
      assertThat(after.getCurrentBalanceCache()).isEqualByComparingTo(ledgerSum(to));
      assertThat(after.getCurrentBalanceCache()).isEqualByComparingTo("1000.00");
    }
  }

  private TransferResponse transfer(Account from, Account to, String amount) {
    return transferService.transfer(
        from.getAccountNumber(),
        to.getAccountNumber(),
        new BigDecimal(amount),
        UUID.randomUUID().toString(),
        null,
        from.getCustomerId());
  }

  /** 다른 트랜잭션이 lockSql로 계좌를 holdMillis 동안 잡고 있는 사이 action을 실행하고 걸린 시간(ms)을 돌려준다. */
  private long whileLocked(String lockSql, Account account, long holdMillis, Callable<?> action)
      throws Exception {
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch locked = new CountDownLatch(1);
    try {
      Future<?> holder =
          executor.submit(
              () ->
                  tx.executeWithoutResult(
                      status -> {
                        jdbcTemplate.queryForObject(lockSql, Integer.class, account.getAccountId());
                        locked.countDown();
                        sleep(holdMillis);
                      }));
      assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
      long started = System.nanoTime();
      executor.submit(action).get(30, TimeUnit.SECONDS);
      long elapsed = (System.nanoTime() - started) / 1_000_000;
      holder.get(30, TimeUnit.SECONDS);
      return elapsed;
    } finally {
      executor.shutdownNow();
    }
  }

  private boolean succeeded(Future<?> future) throws Exception {
    try {
      future.get(30, TimeUnit.SECONDS);
      return true;
    } catch (java.util.concurrent.ExecutionException e) {
      if (e.getCause() instanceof DomainException) {
        return false;
      }
      throw e;
    }
  }

  private BigDecimal balance(Account account) {
    return accountRepository
        .findById(account.getAccountId())
        .orElseThrow()
        .getCurrentBalanceCache();
  }

  private long unapplied(Account account) {
    return pendingCreditRepository.findAll().stream()
        .filter(p -> p.getAccountId().equals(account.getAccountId()) && p.getAppliedAt() == null)
        .count();
  }

  private BigDecimal ledgerSum(Account account) {
    return ledgerEntryRepository.findByAccountId(account.getAccountId()).stream()
        .map(e -> e.getEntryType() == EntryType.CREDIT ? e.getAmount() : e.getAmount().negate())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

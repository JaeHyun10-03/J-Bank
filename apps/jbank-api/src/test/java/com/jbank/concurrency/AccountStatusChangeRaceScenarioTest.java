package com.jbank.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountNumberGenerator;
import com.jbank.account.domain.AccountStatus;
import com.jbank.account.dto.AccountStatusChangeRequest;
import com.jbank.account.service.AccountService;
import com.jbank.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 해지·상태 변경이 잔액 갱신과 겹칠 때 입금이 사라지지 않아야 한다(ADR 0012). 예전에는 락 없이 읽은 옛 잔액으로 전체 컬럼 UPDATE를 해 동시에 반영된 입금을
 * 덮어썼다.
 */
@Import({AccountService.class, AccountNumberGenerator.class})
class AccountStatusChangeRaceScenarioTest extends AbstractConcurrencyTest {

  @Autowired private AccountService accountService;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void 잔액_반영_중에_상태를_바꿔도_반영된_잔액이_남는다() throws Exception {
    Account account = saveAccount(new BigDecimal("1000.00"));

    runWhileCrediting(
        account,
        () ->
            accountService.changeStatus(
                account.getAccountId(),
                new AccountStatusChangeRequest(AccountStatus.SUSPENDED, "테스트")));

    Account updated = accountRepository.findById(account.getAccountId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
    assertThat(updated.getCurrentBalanceCache()).isEqualByComparingTo("1100.00");
  }

  @Test
  void 잔액_반영_중에_해지하면_반영된_잔액을_보고_거절한다() throws Exception {
    Account account = saveAccount(BigDecimal.ZERO);

    assertThatThrownBy(
            () ->
                runWhileCrediting(
                    account,
                    () -> accountService.close(account.getAccountId(), account.getCustomerId())))
        .hasRootCauseMessage(ErrorCode.ACC_008_BALANCE_NOT_ZERO.getMessage());

    Account updated = accountRepository.findById(account.getAccountId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(updated.getCurrentBalanceCache()).isEqualByComparingTo("100.00");
  }

  /** 다른 트랜잭션이 계좌를 잠그고 100원을 반영하는 도중에 action을 실행하고, 반영이 커밋된 뒤 action 결과를 돌려준다. */
  private void runWhileCrediting(Account account, Runnable action) throws Exception {
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch locked = new CountDownLatch(1);
    try {
      Future<?> crediting =
          executor.submit(
              () ->
                  tx.executeWithoutResult(
                      status -> {
                        Account locking =
                            accountRepository
                                .lockForBalanceUpdate(account.getAccountId())
                                .orElseThrow();
                        locking.credit(new BigDecimal("100.00"));
                        accountRepository.saveAndFlush(locking);
                        locked.countDown();
                        sleep(500);
                      }));
      assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
      Future<?> acting = executor.submit(action);
      crediting.get(30, TimeUnit.SECONDS);
      acting.get(30, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

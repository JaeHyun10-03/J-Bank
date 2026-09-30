package com.jbank.batch.reconciliation;

import com.jbank.account.domain.Account;
import com.jbank.account.repository.AccountRepository;
import com.jbank.ledger.domain.EntryType;
import com.jbank.ledger.repository.AccountLedgerBalance;
import com.jbank.ledger.repository.LedgerEntryRepository;
import com.jbank.transfer.repository.PendingCreditRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * 전 계좌의 원장 합산 값과 캐시 잔액을 비교하고, 전체 차변/대변 합이 일치하는지 검증한다(구현계획 W5). 이체 입금은 수신 계좌에 비동기로 반영되므로(ADR 0012)
 * 차변은 이미 기록됐는데 대변은 아직인 금액(미반영 입금 대기)을 더해 비교하고, 1분 넘게 반영되지 않은 입금은 경고한다. 쿼리 사이에 반영이 끼어 오탐이 나지 않게 스텝은
 * REPEATABLE READ 한 트랜잭션에서 돈다(LedgerReconciliationJobConfig). 불일치는 자동 보정하지 않고 로그로만 남긴다 — 금융 시스템에서
 * 정합성 불일치를 자동 보정하는 것은 원인을 은폐하는 행위이기 때문이다.
 */
public class LedgerReconciliationTasklet implements Tasklet {

  private static final Logger log = LoggerFactory.getLogger(LedgerReconciliationTasklet.class);

  static final long STALE_PENDING_SECONDS = 60;

  private final AccountRepository accountRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final PendingCreditRepository pendingCreditRepository;

  public LedgerReconciliationTasklet(
      AccountRepository accountRepository,
      LedgerEntryRepository ledgerEntryRepository,
      PendingCreditRepository pendingCreditRepository) {
    this.accountRepository = accountRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.pendingCreditRepository = pendingCreditRepository;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    int mismatchCount = reconcileAccountBalances();
    boolean globalMismatch = reconcileGlobalDebitCredit();

    log.info(
        "원장 정합성 대사 완료: 계좌 불일치 {}건, 전체 차변/대변 불일치 {}", mismatchCount, globalMismatch ? "있음" : "없음");
    return RepeatStatus.FINISHED;
  }

  private int reconcileAccountBalances() {
    Map<Long, BigDecimal> ledgerBalances =
        ledgerEntryRepository.sumBalanceByAccount().stream()
            .collect(
                Collectors.toMap(
                    AccountLedgerBalance::accountId, AccountLedgerBalance::ledgerBalance));

    int mismatchCount = 0;
    for (Account account : accountRepository.findAll()) {
      BigDecimal ledgerBalance =
          ledgerBalances.getOrDefault(account.getAccountId(), BigDecimal.ZERO);
      if (ledgerBalance.compareTo(account.getCurrentBalanceCache()) != 0) {
        mismatchCount++;
        log.warn(
            "계좌 잔액 불일치: accountId={}, 캐시잔액={}, 원장합산={}",
            account.getAccountId(),
            account.getCurrentBalanceCache(),
            ledgerBalance);
      }
    }
    return mismatchCount;
  }

  private boolean reconcileGlobalDebitCredit() {
    BigDecimal totalDebit = ledgerEntryRepository.sumAmountByEntryType(EntryType.DEBIT);
    BigDecimal totalCredit = ledgerEntryRepository.sumAmountByEntryType(EntryType.CREDIT);
    BigDecimal unappliedCredit = pendingCreditRepository.sumUnapplied();
    boolean mismatch = totalDebit.compareTo(totalCredit.add(unappliedCredit)) != 0;
    if (mismatch) {
      log.warn(
          "전체 차변/대변 합 불일치: 차변합={}, 대변합={}, 미반영입금={}", totalDebit, totalCredit, unappliedCredit);
    }
    long stale = pendingCreditRepository.countUnappliedOlderThan(STALE_PENDING_SECONDS);
    if (stale > 0) {
      log.warn("1분 넘게 반영되지 않은 입금 대기: {}건", stale);
    }
    return mismatch;
  }
}

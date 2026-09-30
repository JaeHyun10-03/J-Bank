package com.jbank.transfer.service;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountStatus;
import com.jbank.account.repository.AccountRepository;
import com.jbank.ledger.domain.EntryType;
import com.jbank.ledger.domain.LedgerEntry;
import com.jbank.ledger.repository.LedgerEntryRepository;
import com.jbank.transfer.domain.PendingCredit;
import com.jbank.transfer.repository.PendingCreditRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 수신 계좌의 미반영 입금을 모아 반영한다(docs/adr/0012). 계좌 행을 한 번만 잠그고, 거래마다 대변 원장 1건(반영 후 잔액 누적 스냅샷)을 남긴 뒤 반영
 * 표시를 한다. 계좌마다 새 트랜잭션이라 한 계좌의 실패가 다른 계좌 반영을 막지 않는다.
 */
@Service
public class CreditApplier {

  static final int BATCH_LIMIT = 500;

  private static final Logger log = LoggerFactory.getLogger(CreditApplier.class);

  private final AccountRepository accountRepository;
  private final PendingCreditRepository pendingCreditRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final Timer applyLag;

  public CreditApplier(
      AccountRepository accountRepository,
      PendingCreditRepository pendingCreditRepository,
      LedgerEntryRepository ledgerEntryRepository,
      MeterRegistry meterRegistry) {
    this.accountRepository = accountRepository;
    this.pendingCreditRepository = pendingCreditRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.applyLag =
        Timer.builder("jbank.credit.apply.lag")
            .description("입금 대기 생성부터 반영까지 걸린 시간")
            .publishPercentileHistogram()
            .register(meterRegistry);
  }

  /** 이 계좌의 미반영 입금을 최대 {@link #BATCH_LIMIT}건 반영하고 반영한 건수를 돌려준다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int applyForAccount(Long accountId) {
    // 이 트랜잭션에서 계좌를 처음 읽는 쿼리가 곧 락 쿼리여야 한다 — 먼저 다른 방법으로 읽어 두면
    // 영속성 컨텍스트가 옛 잔액을 돌려준다.
    Account account = accountRepository.lockForBalanceUpdate(accountId).orElse(null);
    if (account == null) {
      return 0;
    }
    if (account.getStatus() == AccountStatus.CLOSED) {
      // 해지는 처리 중인 입금이 있으면 거절하므로 오면 안 되는 경로다. 반영하지 않고 대사 경고로 드러낸다.
      log.error("해지된 계좌에 미반영 입금이 있어 반영하지 않음: accountId={}", accountId);
      return 0;
    }
    List<PendingCredit> pending =
        pendingCreditRepository.lockUnappliedForAccount(accountId, BATCH_LIMIT);
    if (pending.isEmpty()) {
      return 0;
    }
    OffsetDateTime occurredAt = OffsetDateTime.now();
    for (PendingCredit credit : pending) {
      account.credit(credit.getAmount());
      ledgerEntryRepository.save(
          new LedgerEntry(
              accountId,
              credit.getTransactionId(),
              EntryType.CREDIT,
              credit.getAmount(),
              account.getCurrentBalanceCache(),
              occurredAt));
    }
    pendingCreditRepository.markApplied(
        pending.stream().map(PendingCredit::getPendingCreditId).toList());
    for (PendingCredit credit : pending) {
      applyLag.record(Duration.between(credit.getCreatedAt(), occurredAt).abs());
    }
    return pending.size();
  }
}

package com.jbank.batch.interest;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountException;
import com.jbank.account.repository.AccountRepository;
import com.jbank.global.exception.ErrorCode;
import com.jbank.ledger.domain.EntryType;
import com.jbank.ledger.domain.LedgerEntry;
import com.jbank.ledger.repository.LedgerEntryRepository;
import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionType;
import com.jbank.transfer.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * 계약 하나당 이자 원장 입금·거래 기록을 한 청크 트랜잭션 안에서 처리하고, 로컬 커밋이
 * 끝난 뒤에야 jbank-product에 만기 확정을 알린다.
 *
 * <p>product 모듈 분리 전에는 이자 입금과 계약 만기 전환이 같은 로컬 트랜잭션
 * 안에서 원자적으로 묶여 있었다. 분리 이후에는 만기 확정이 네트워크 호출이라 그
 * 원자성이 깨진다 — 이자는 입금됐는데 만기 확정 호출만 실패할 수 있다. 그래서
 * idempotencyKey를 실행일(runDate)이 아니라 계약 하나당 한 번만 존재하도록
 * 고정해서, 확정 호출이 실패해 다음 실행에서 같은 계약이 다시 조회되더라도
 * 이자가 중복 지급되지 않게 한다 — 이미 발급된 거래를 찾으면 입금은 건너뛰고
 * 확정 호출만 재시도한다.
 */
@Component
public class MaturedContractInterestItemWriter implements ItemWriter<MaturedContractDto> {

  private final AccountRepository accountRepository;
  private final TransactionRepository transactionRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final MaturedContractApiClient maturedContractApiClient;

  public MaturedContractInterestItemWriter(
      AccountRepository accountRepository,
      TransactionRepository transactionRepository,
      LedgerEntryRepository ledgerEntryRepository,
      MaturedContractApiClient maturedContractApiClient) {
    this.accountRepository = accountRepository;
    this.transactionRepository = transactionRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.maturedContractApiClient = maturedContractApiClient;
  }

  @Override
  public void write(Chunk<? extends MaturedContractDto> chunk) {
    for (MaturedContractDto item : chunk) {
      creditInterest(item);
      maturedContractApiClient.markMatured(item.contractId());
    }
  }

  private void creditInterest(MaturedContractDto item) {
    String idempotencyKey = "INTEREST-" + item.contractId();
    if (transactionRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
      return; // 이미 입금됨 — 이전 실행에서 markMatured 호출만 실패했던 경우
    }

    Account account =
        accountRepository
            .findByIdForUpdate(item.accountId())
            .orElseThrow(() -> new AccountException(ErrorCode.COMMON_004_NOT_FOUND));

    BigDecimal interestAmount = item.interestAmount();
    Transaction transaction =
        new Transaction(
            TransactionType.INTEREST,
            null,
            account.getAccountId(),
            interestAmount,
            idempotencyKey,
            "만기 이자 지급(계약 " + item.contractId() + ")");
    transaction = transactionRepository.save(transaction);

    OffsetDateTime occurredAt = OffsetDateTime.now();
    account.credit(interestAmount);
    ledgerEntryRepository.save(
        new LedgerEntry(
            account.getAccountId(),
            transaction.getTransactionId(),
            EntryType.CREDIT,
            interestAmount,
            account.getCurrentBalanceCache(),
            occurredAt));
    transaction.complete(occurredAt);
  }
}

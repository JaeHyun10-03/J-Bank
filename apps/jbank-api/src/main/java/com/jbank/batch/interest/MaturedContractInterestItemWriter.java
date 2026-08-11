package com.jbank.batch.interest;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountException;
import com.jbank.account.repository.AccountRepository;
import com.jbank.global.exception.ErrorCode;
import com.jbank.ledger.domain.EntryType;
import com.jbank.ledger.domain.LedgerEntry;
import com.jbank.ledger.repository.LedgerEntryRepository;
import com.jbank.product.domain.ProductContract;
import com.jbank.product.repository.ProductContractRepository;
import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionType;
import com.jbank.transfer.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 계약 하나당 이자 원장 입금·거래 기록·계약 만기 전환을 한 청크 트랜잭션 안에서 처리한다. */
@Component
@StepScope
public class MaturedContractInterestItemWriter implements ItemWriter<MaturedContractInterest> {

  private final AccountRepository accountRepository;
  private final TransactionRepository transactionRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final ProductContractRepository productContractRepository;
  private final String runDate;

  public MaturedContractInterestItemWriter(
      AccountRepository accountRepository,
      TransactionRepository transactionRepository,
      LedgerEntryRepository ledgerEntryRepository,
      ProductContractRepository productContractRepository,
      @Value("#{jobParameters['runDate']}") String runDate) {
    this.accountRepository = accountRepository;
    this.transactionRepository = transactionRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.productContractRepository = productContractRepository;
    this.runDate = runDate;
  }

  @Override
  public void write(Chunk<? extends MaturedContractInterest> chunk) {
    for (MaturedContractInterest item : chunk) {
      creditInterest(item.contract(), item.interestAmount());
    }
  }

  private void creditInterest(ProductContract contract, BigDecimal interestAmount) {
    Account account =
        accountRepository
            .findByIdForUpdate(contract.getAccountId())
            .orElseThrow(() -> new AccountException(ErrorCode.COMMON_004_NOT_FOUND));

    Transaction transaction =
        new Transaction(
            TransactionType.INTEREST,
            null,
            account.getAccountId(),
            interestAmount,
            "INTEREST-" + contract.getContractId() + "-" + runDate,
            "만기 이자 지급(계약 " + contract.getContractId() + ")");
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

    contract.markMatured();
    productContractRepository.save(contract);
  }
}

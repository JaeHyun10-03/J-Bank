package com.jbank.transfer.service;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountException;
import com.jbank.account.domain.AccountStatus;
import com.jbank.account.repository.AccountRepository;
import com.jbank.global.exception.ErrorCode;
import com.jbank.ledger.domain.EntryType;
import com.jbank.ledger.domain.LedgerEntry;
import com.jbank.ledger.repository.LedgerEntryRepository;
import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionException;
import com.jbank.transfer.domain.TransactionType;
import com.jbank.transfer.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

  private final AccountRepository accountRepository;
  private final TransactionRepository transactionRepository;
  private final LedgerEntryRepository ledgerEntryRepository;

  public TransferService(
      AccountRepository accountRepository,
      TransactionRepository transactionRepository,
      LedgerEntryRepository ledgerEntryRepository) {
    this.accountRepository = accountRepository;
    this.transactionRepository = transactionRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
  }

  @Transactional
  public Transaction transfer(
      String fromAccountNumber,
      String toAccountNumber,
      BigDecimal amount,
      String idempotencyKey,
      String memo) {
    if (fromAccountNumber.equals(toAccountNumber)) {
      throw new TransactionException(ErrorCode.TXN_002_SAME_ACCOUNT_TRANSFER);
    }

    List<String> lockOrder = List.of(fromAccountNumber, toAccountNumber).stream().sorted().toList();
    Account first = lockAccount(lockOrder.get(0), toAccountNumber);
    Account second = lockAccount(lockOrder.get(1), toAccountNumber);
    Account from = fromAccountNumber.equals(first.getAccountNumber()) ? first : second;
    Account to = fromAccountNumber.equals(first.getAccountNumber()) ? second : first;

    if (from.getStatus() != AccountStatus.ACTIVE) {
      throw new AccountException(ErrorCode.ACC_009_ACCOUNT_STATUS_INVALID);
    }
    if (to.getStatus() != AccountStatus.ACTIVE) {
      throw new TransactionException(ErrorCode.TXN_004_COUNTERPARTY_ACCOUNT_INVALID);
    }
    if (from.getAvailableBalance().compareTo(amount) < 0) {
      throw new TransactionException(ErrorCode.TXN_001_INSUFFICIENT_BALANCE);
    }

    Transaction transaction =
        new Transaction(
            TransactionType.TRANSFER,
            from.getAccountId(),
            to.getAccountId(),
            amount,
            idempotencyKey,
            memo);
    transaction = transactionRepository.save(transaction);

    OffsetDateTime occurredAt = OffsetDateTime.now();
    from.debit(amount);
    to.credit(amount);
    ledgerEntryRepository.save(
        new LedgerEntry(
            from.getAccountId(),
            transaction.getTransactionId(),
            EntryType.DEBIT,
            amount,
            from.getCurrentBalanceCache(),
            occurredAt));
    ledgerEntryRepository.save(
        new LedgerEntry(
            to.getAccountId(),
            transaction.getTransactionId(),
            EntryType.CREDIT,
            amount,
            to.getCurrentBalanceCache(),
            occurredAt));
    transaction.complete(occurredAt);

    return transaction;
  }

  private Account lockAccount(String accountNumber, String toAccountNumber) {
    return accountRepository
        .findByAccountNumberForUpdate(accountNumber)
        .orElseThrow(
            () ->
                accountNumber.equals(toAccountNumber)
                    ? new TransactionException(ErrorCode.TXN_003_COUNTERPARTY_ACCOUNT_NOT_FOUND)
                    : new AccountException(ErrorCode.COMMON_004_NOT_FOUND));
  }
}

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
import com.jbank.transfer.domain.TransactionType;
import com.jbank.transfer.dto.AccountTransactionResponse;
import com.jbank.transfer.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositService {

  private final AccountRepository accountRepository;
  private final TransactionRepository transactionRepository;
  private final LedgerEntryRepository ledgerEntryRepository;

  public DepositService(
      AccountRepository accountRepository,
      TransactionRepository transactionRepository,
      LedgerEntryRepository ledgerEntryRepository) {
    this.accountRepository = accountRepository;
    this.transactionRepository = transactionRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
  }

  @Transactional
  public AccountTransactionResponse deposit(
      Long accountId, BigDecimal amount, String idempotencyKey) {
    Transaction existing = transactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
    if (existing != null) {
      return toResponse(existing, resolveBalanceAfter(existing));
    }

    Account account =
        accountRepository
            .findByIdForUpdate(accountId)
            .orElseThrow(() -> new AccountException(ErrorCode.COMMON_004_NOT_FOUND));
    if (account.getStatus() != AccountStatus.ACTIVE) {
      throw new AccountException(ErrorCode.ACC_009_ACCOUNT_STATUS_INVALID);
    }

    Transaction transaction =
        new Transaction(
            TransactionType.DEPOSIT, null, account.getAccountId(), amount, idempotencyKey, null);
    try {
      transaction = transactionRepository.save(transaction);
    } catch (DataIntegrityViolationException e) {
      Transaction raced =
          transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
      return toResponse(raced, resolveBalanceAfter(raced));
    }

    OffsetDateTime occurredAt = OffsetDateTime.now();
    account.credit(amount);
    ledgerEntryRepository.save(
        new LedgerEntry(
            account.getAccountId(),
            transaction.getTransactionId(),
            EntryType.CREDIT,
            amount,
            account.getCurrentBalanceCache(),
            occurredAt));
    transaction.complete(occurredAt);

    return toResponse(transaction, account.getCurrentBalanceCache());
  }

  private BigDecimal resolveBalanceAfter(Transaction transaction) {
    return accountRepository
        .findById(transaction.getToAccountId())
        .map(Account::getCurrentBalanceCache)
        .orElseThrow(() -> new AccountException(ErrorCode.COMMON_004_NOT_FOUND));
  }

  private AccountTransactionResponse toResponse(Transaction transaction, BigDecimal balanceAfter) {
    return new AccountTransactionResponse(
        String.valueOf(transaction.getTransactionId()),
        String.valueOf(transaction.getToAccountId()),
        transaction.getTransactionType(),
        transaction.getAmount(),
        balanceAfter,
        transaction.getProcessedAt());
  }
}

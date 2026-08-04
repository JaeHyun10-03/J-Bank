package com.jbank.transfer.service;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountException;
import com.jbank.account.domain.AccountStatus;
import com.jbank.account.repository.AccountRepository;
import com.jbank.common.event.TransferCompletedEvent;
import com.jbank.global.exception.ErrorCode;
import com.jbank.ledger.domain.EntryType;
import com.jbank.ledger.domain.LedgerEntry;
import com.jbank.ledger.repository.LedgerEntryRepository;
import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionException;
import com.jbank.transfer.domain.TransactionType;
import com.jbank.transfer.dto.TransferResponse;
import com.jbank.transfer.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

  private final AccountRepository accountRepository;
  private final TransactionRepository transactionRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final IdempotencyRecovery idempotencyRecovery;
  private final ApplicationEventPublisher eventPublisher;

  public TransferService(
      AccountRepository accountRepository,
      TransactionRepository transactionRepository,
      LedgerEntryRepository ledgerEntryRepository,
      IdempotencyRecovery idempotencyRecovery,
      ApplicationEventPublisher eventPublisher) {
    this.accountRepository = accountRepository;
    this.transactionRepository = transactionRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.idempotencyRecovery = idempotencyRecovery;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public TransferResponse transfer(
      String fromAccountNumber,
      String toAccountNumber,
      BigDecimal amount,
      String idempotencyKey,
      String memo,
      Long requestingCustomerId) {
    Transaction existing = transactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
    if (existing != null) {
      return toResponse(existing, resolveFromBalance(existing, requestingCustomerId));
    }

    if (fromAccountNumber.equals(toAccountNumber)) {
      throw new TransactionException(ErrorCode.TXN_002_SAME_ACCOUNT_TRANSFER);
    }

    List<String> lockOrder = List.of(fromAccountNumber, toAccountNumber).stream().sorted().toList();
    Account first = lockAccount(lockOrder.get(0), toAccountNumber);
    Account second = lockAccount(lockOrder.get(1), toAccountNumber);
    Account from = fromAccountNumber.equals(first.getAccountNumber()) ? first : second;
    Account to = fromAccountNumber.equals(first.getAccountNumber()) ? second : first;

    if (!from.getCustomerId().equals(requestingCustomerId)) {
      throw new AccountException(ErrorCode.COMMON_003_FORBIDDEN);
    }
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
    try {
      transaction = transactionRepository.save(transaction);
    } catch (DataIntegrityViolationException e) {
      // 사전 조회 통과 후 동시에 같은 키로 들어온 요청이 유니크 제약에 걸린 경우, 먼저
      // 커밋된 원래 결과를 그대로 반환해 완전한 멱등을 보장한다. PostgreSQL은 제약 위반
      // 이후 현재 트랜잭션 전체를 포기 상태로 만들어 같은 세션으로는 조회조차 안 되므로
      // 새 트랜잭션에서 조회한다(IdempotencyRecovery 참고).
      return idempotencyRecovery.recoverInNewTransaction(
          () -> {
            Transaction raced =
                transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
            return toResponse(raced, resolveFromBalance(raced, requestingCustomerId));
          });
    }

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
    eventPublisher.publishEvent(
        new TransferCompletedEvent(
            transaction.getTransactionId(),
            from.getAccountId(),
            to.getAccountId(),
            amount,
            occurredAt));

    return toResponse(transaction, from.getCurrentBalanceCache());
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

  private BigDecimal resolveFromBalance(Transaction transaction, Long requestingCustomerId) {
    Account account =
        accountRepository
            .findById(transaction.getFromAccountId())
            .orElseThrow(() -> new AccountException(ErrorCode.COMMON_004_NOT_FOUND));
    if (!account.getCustomerId().equals(requestingCustomerId)) {
      throw new AccountException(ErrorCode.COMMON_003_FORBIDDEN);
    }
    return account.getCurrentBalanceCache();
  }

  private TransferResponse toResponse(Transaction transaction, BigDecimal fromAccountBalanceAfter) {
    return new TransferResponse(
        String.valueOf(transaction.getTransactionId()),
        transaction.getStatus(),
        fromAccountBalanceAfter,
        transaction.getProcessedAt());
  }
}

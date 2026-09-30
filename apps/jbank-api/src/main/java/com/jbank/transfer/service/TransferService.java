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
import com.jbank.transfer.domain.PendingCredit;
import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionException;
import com.jbank.transfer.domain.TransactionStatus;
import com.jbank.transfer.domain.TransactionType;
import com.jbank.transfer.dto.TransferResponse;
import com.jbank.transfer.repository.PendingCreditRepository;
import com.jbank.transfer.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

  private final AccountRepository accountRepository;
  private final TransactionRepository transactionRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final PendingCreditRepository pendingCreditRepository;
  private final IdempotencyRecovery idempotencyRecovery;
  private final ApplicationEventPublisher eventPublisher;
  private final OtpService otpService;
  private final BigDecimal otpThresholdAmount;

  public TransferService(
      AccountRepository accountRepository,
      TransactionRepository transactionRepository,
      LedgerEntryRepository ledgerEntryRepository,
      PendingCreditRepository pendingCreditRepository,
      IdempotencyRecovery idempotencyRecovery,
      ApplicationEventPublisher eventPublisher,
      OtpService otpService,
      @Value("${jbank.transfer.otp.threshold-amount:10000000}") BigDecimal otpThresholdAmount) {
    this.accountRepository = accountRepository;
    this.transactionRepository = transactionRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.pendingCreditRepository = pendingCreditRepository;
    this.idempotencyRecovery = idempotencyRecovery;
    this.eventPublisher = eventPublisher;
    this.otpService = otpService;
    this.otpThresholdAmount = otpThresholdAmount;
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

    // 두 계좌를 계좌번호 순서로 잠근다(FR-TXN-003). 송금 계좌는 잔액을 바꾸므로 NO KEY UPDATE, 수신 계좌는
    // 존재·상태만 확인하므로 KEY SHARE다 — 입금은 반영 워커가 따로 하므로(ADR 0012) 한 계좌로 이체가 몰려도
    // 수신 계좌 행에서 줄 서지 않는다.
    Account from;
    Account to;
    if (fromAccountNumber.compareTo(toAccountNumber) < 0) {
      from = lockSender(fromAccountNumber);
      to = lockReceiver(toAccountNumber);
    } else {
      to = lockReceiver(toAccountNumber);
      from = lockSender(fromAccountNumber);
    }

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

    if (amount.compareTo(otpThresholdAmount) > 0) {
      // 임계금액 초과는 즉시 원장에 반영하지 않고 인증 대기로 남긴다(FR-AUTH-003). 대기 중인
      // 금액을 지급정지로 잡아두지 않으면 같은 잔액으로 여러 건의 대기 거래를 만들 수 있다.
      from.hold(amount);
      transaction.markPendingOtp();
      otpService.issue(transaction.getTransactionId());
      return toResponse(transaction, from.getCurrentBalanceCache());
    }

    executeTransfer(transaction, from, to);
    return toResponse(transaction, from.getCurrentBalanceCache());
  }

  /**
   * OTP 검증 성공(API-016) 후 대기 중이던 이체를 마무리한다. 지급정지를 해제하고 원장 두 건을 남겨 완료 전이하는 로직은 임계금액 이하 즉시 완료 경로와 동일해
   * executeTransfer로 공유한다.
   */
  @Transactional
  public TransferResponse completeAfterOtp(Long transactionId) {
    Transaction transaction =
        transactionRepository
            .findByIdForUpdate(transactionId)
            .orElseThrow(() -> new TransactionException(ErrorCode.COMMON_004_NOT_FOUND));
    if (transaction.getStatus() != TransactionStatus.PENDING_OTP) {
      throw new TransactionException(ErrorCode.TXN_005_TRANSACTION_NOT_PENDING);
    }

    // 즉시 이체와 같은 락 모드(송금 NO KEY UPDATE, 수신 KEY SHARE). 수신 계좌 상태는 다시 보지 않는다 —
    // 인증 대기 이체가 들어올 계좌는 해지가 거절되므로(ACC_012) 여기서 해지된 계좌를 만날 수 없다.
    Account from;
    Account to;
    if (transaction.getFromAccountId() < transaction.getToAccountId()) {
      from = lockSender(transaction.getFromAccountId());
      to = lockReceiver(transaction.getToAccountId());
    } else {
      to = lockReceiver(transaction.getToAccountId());
      from = lockSender(transaction.getFromAccountId());
    }

    from.release(transaction.getAmount());
    executeTransfer(transaction, from, to);
    return toResponse(transaction, from.getCurrentBalanceCache());
  }

  private void executeTransfer(Transaction transaction, Account from, Account to) {
    BigDecimal amount = transaction.getAmount();
    OffsetDateTime occurredAt = OffsetDateTime.now();
    from.debit(amount);
    ledgerEntryRepository.save(
        new LedgerEntry(
            from.getAccountId(),
            transaction.getTransactionId(),
            EntryType.DEBIT,
            amount,
            from.getCurrentBalanceCache(),
            occurredAt));
    // 수신 측은 입금 대기로 남기고 반영 워커가 대변 원장·잔액에 반영한다(ADR 0012). 같은 트랜잭션이라
    // 송금이 커밋되면 입금 대기도 반드시 남는다.
    pendingCreditRepository.save(
        new PendingCredit(transaction.getTransactionId(), to.getAccountId(), amount));
    transaction.complete(occurredAt);
    eventPublisher.publishEvent(
        new TransferCompletedEvent(
            transaction.getTransactionId(),
            from.getAccountId(),
            to.getAccountId(),
            amount,
            occurredAt));
  }

  private Account lockSender(String accountNumber) {
    return accountRepository
        .lockSenderByAccountNumber(accountNumber)
        .orElseThrow(() -> new AccountException(ErrorCode.COMMON_004_NOT_FOUND));
  }

  private Account lockReceiver(String accountNumber) {
    return accountRepository
        .lockReceiverByAccountNumber(accountNumber)
        .orElseThrow(
            () -> new TransactionException(ErrorCode.TXN_003_COUNTERPARTY_ACCOUNT_NOT_FOUND));
  }

  private Account lockSender(Long accountId) {
    return accountRepository
        .lockForBalanceUpdate(accountId)
        .orElseThrow(() -> new AccountException(ErrorCode.COMMON_004_NOT_FOUND));
  }

  private Account lockReceiver(Long accountId) {
    return accountRepository
        .lockReceiverById(accountId)
        .orElseThrow(() -> new AccountException(ErrorCode.COMMON_004_NOT_FOUND));
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

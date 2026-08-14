package com.jbank.transfer.service;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountException;
import com.jbank.account.repository.AccountRepository;
import com.jbank.global.exception.ErrorCode;
import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionException;
import com.jbank.transfer.domain.TransactionStatus;
import com.jbank.transfer.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 대기(PENDING_OTP) 거래를 취소하고 지급정지를 롤백한다(FR-AUTH-003). OTP 검증 실패한도초과·만료 처리와
 * 만료 대기 거래 정리 스케줄러가 공용으로 쓴다. 이미 다른 경로로 처리된 거래는 조용히 넘어간다 — 두 호출자가
 * 같은 거래를 동시에 취소하려는 경쟁을 대비한 안전장치다.
 */
@Service
public class PendingOtpCancellationService {

  private final TransactionRepository transactionRepository;
  private final AccountRepository accountRepository;

  public PendingOtpCancellationService(
      TransactionRepository transactionRepository, AccountRepository accountRepository) {
    this.transactionRepository = transactionRepository;
    this.accountRepository = accountRepository;
  }

  @Transactional
  public void cancel(Long transactionId) {
    Transaction transaction =
        transactionRepository
            .findByIdForUpdate(transactionId)
            .orElseThrow(() -> new TransactionException(ErrorCode.COMMON_004_NOT_FOUND));
    if (transaction.getStatus() != TransactionStatus.PENDING_OTP) {
      return;
    }

    Account from =
        accountRepository
            .findByIdForUpdate(transaction.getFromAccountId())
            .orElseThrow(() -> new AccountException(ErrorCode.COMMON_004_NOT_FOUND));
    from.release(transaction.getAmount());
    transaction.cancel();
  }
}

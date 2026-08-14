package com.jbank.transfer.service;

import com.jbank.account.domain.Account;
import com.jbank.account.domain.AccountException;
import com.jbank.account.repository.AccountRepository;
import com.jbank.global.exception.ErrorCode;
import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionException;
import com.jbank.transfer.domain.TransactionStatus;
import com.jbank.transfer.dto.TransferResponse;
import com.jbank.transfer.repository.TransactionRepository;
import org.springframework.stereotype.Service;

/**
 * API-016 OTP 검증(FR-AUTH-003) 오케스트레이션. 원장 반영은 TransferService.completeAfterOtp,
 * 취소·지급정지 롤백은 PendingOtpCancellationService에 위임한다. 두 위임 대상 모두 각자
 * @Transactional로 독립 커밋되므로, 이 클래스는 트랜잭션을 직접 열지 않는다 — 실패한도초과·만료는
 * 상태 변경이 이미 커밋된 뒤에 예외를 던져야 응답 실패가 방금 커밋한 취소를 되돌리지 않는다.
 */
@Service
public class OtpVerificationService {

  private final TransactionRepository transactionRepository;
  private final AccountRepository accountRepository;
  private final OtpService otpService;
  private final TransferService transferService;
  private final PendingOtpCancellationService pendingOtpCancellationService;

  public OtpVerificationService(
      TransactionRepository transactionRepository,
      AccountRepository accountRepository,
      OtpService otpService,
      TransferService transferService,
      PendingOtpCancellationService pendingOtpCancellationService) {
    this.transactionRepository = transactionRepository;
    this.accountRepository = accountRepository;
    this.otpService = otpService;
    this.transferService = transferService;
    this.pendingOtpCancellationService = pendingOtpCancellationService;
  }

  public TransferResponse verify(Long transactionId, String otpCode, Long requestingCustomerId) {
    Transaction transaction =
        transactionRepository
            .findById(transactionId)
            .orElseThrow(() -> new TransactionException(ErrorCode.COMMON_004_NOT_FOUND));
    if (transaction.getStatus() != TransactionStatus.PENDING_OTP) {
      throw new TransactionException(ErrorCode.TXN_005_TRANSACTION_NOT_PENDING);
    }
    Account from =
        accountRepository
            .findById(transaction.getFromAccountId())
            .orElseThrow(() -> new AccountException(ErrorCode.COMMON_004_NOT_FOUND));
    if (!from.getCustomerId().equals(requestingCustomerId)) {
      throw new AccountException(ErrorCode.COMMON_003_FORBIDDEN);
    }

    OtpService.VerifyResult result = otpService.verify(transactionId, otpCode);
    return switch (result) {
      case MATCH -> transferService.completeAfterOtp(transactionId);
      case MISMATCH -> throw new TransactionException(ErrorCode.AUTH_004_OTP_MISMATCH);
      case LIMIT_EXCEEDED -> {
        pendingOtpCancellationService.cancel(transactionId);
        throw new TransactionException(ErrorCode.AUTH_004_OTP_MISMATCH);
      }
      case EXPIRED -> {
        pendingOtpCancellationService.cancel(transactionId);
        throw new TransactionException(ErrorCode.AUTH_005_OTP_EXPIRED);
      }
    };
  }
}

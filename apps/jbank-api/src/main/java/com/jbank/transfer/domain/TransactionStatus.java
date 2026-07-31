package com.jbank.transfer.domain;

/**
 * ERD 문서 2.4절의 상태 기계. COMPLETED, FAILED, CANCELLED는 종료 상태이며 어떤 전이도 허용하지 않는다. PENDING_OTP 관련 전이는
 * W5에서 실제로 쓰이지만, 규칙 자체는 처음부터 전체를 정의해둔다.
 */
public enum TransactionStatus {
  PENDING,
  PENDING_OTP,
  COMPLETED,
  FAILED,
  CANCELLED;

  public boolean canTransitionTo(TransactionStatus target) {
    return switch (this) {
      case PENDING -> target == COMPLETED || target == PENDING_OTP || target == FAILED;
      case PENDING_OTP -> target == COMPLETED || target == CANCELLED;
      case COMPLETED, FAILED, CANCELLED -> false;
    };
  }
}

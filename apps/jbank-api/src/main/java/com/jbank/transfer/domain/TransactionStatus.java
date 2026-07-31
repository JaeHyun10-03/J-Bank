package com.jbank.transfer.domain;

public enum TransactionStatus {
  PENDING,
  PENDING_OTP,
  COMPLETED,
  FAILED,
  CANCELLED
}

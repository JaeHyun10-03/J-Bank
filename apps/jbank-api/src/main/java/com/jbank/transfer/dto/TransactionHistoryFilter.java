package com.jbank.transfer.dto;

/** API-010 조회 파라미터의 type 값. TRANSFER_IN/OUT은 이체 거래를 조회 대상 계좌 기준 방향으로 나눈 것이다. */
public enum TransactionHistoryFilter {
  DEPOSIT,
  WITHDRAWAL,
  TRANSFER_IN,
  TRANSFER_OUT
}

package com.jbank.product.domain;

public enum ContractStatus {
  /** 사가 진행 중 — 출금 요청을 보냈지만 아직 확정되지 않음. */
  PENDING,
  ACTIVE,
  MATURED,
  TERMINATED,
  /** 출금까지는 성공했으나 확정 단계가 실패해 보상 거래(출금 롤백)로 되돌린 상태(감사 기록용, 삭제하지 않음). */
  FAILED
}

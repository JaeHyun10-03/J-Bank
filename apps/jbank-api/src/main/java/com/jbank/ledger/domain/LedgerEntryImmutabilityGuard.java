package com.jbank.ledger.domain;

import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;

/** 원장은 append-only라 리포지토리에서 delete류를 노출하지 않는 것이 1차 방어고, 이 리스너가 2차 방어다. */
public class LedgerEntryImmutabilityGuard {

  @PreUpdate
  public void onUpdate(LedgerEntry entry) {
    throw new UnsupportedOperationException("원장 엔트리는 수정할 수 없습니다");
  }

  @PreRemove
  public void onRemove(LedgerEntry entry) {
    throw new UnsupportedOperationException("원장 엔트리는 삭제할 수 없습니다");
  }
}

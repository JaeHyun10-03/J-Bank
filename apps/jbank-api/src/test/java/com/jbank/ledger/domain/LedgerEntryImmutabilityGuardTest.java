package com.jbank.ledger.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class LedgerEntryImmutabilityGuardTest {

  private final LedgerEntryImmutabilityGuard guard = new LedgerEntryImmutabilityGuard();
  private final LedgerEntry entry =
      new LedgerEntry(
          1L,
          1L,
          EntryType.DEBIT,
          new BigDecimal("1000.00"),
          new BigDecimal("9000.00"),
          OffsetDateTime.now());

  @Test
  void 수정을_시도하면_예외를_던진다() {
    assertThatThrownBy(() -> guard.onUpdate(entry))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void 삭제를_시도하면_예외를_던진다() {
    assertThatThrownBy(() -> guard.onRemove(entry))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}

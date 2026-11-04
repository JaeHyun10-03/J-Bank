package com.jbank.concurrency;

import com.jbank.transfer.service.TransferLockStrategy.Mode;
import org.springframework.test.context.TestPropertySource;

/** 정렬 + FOR UPDATE. 락 모드는 막히지만 순서가 같아 교착 0·실패 0. */
@TestPropertySource(properties = "jbank.transfer.lock-strategy=sorted-for-update")
class SortedForUpdateLockStrategyScenarioTest extends TransferLockStrategyScenarioTest {

  @Override
  Mode expectedMode() {
    return Mode.SORTED_FOR_UPDATE;
  }
}

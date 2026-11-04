package com.jbank.concurrency;

import com.jbank.transfer.service.TransferLockStrategy.Mode;
import org.springframework.test.context.TestPropertySource;

/** 정렬 없음 + FOR UPDATE(수정 전 방식 재현). 양방향 동시 이체에서 교착(40P01)이 난다. */
@TestPropertySource(properties = "jbank.transfer.lock-strategy=unsorted-for-update")
class UnsortedForUpdateLockStrategyScenarioTest extends TransferLockStrategyScenarioTest {

  @Override
  Mode expectedMode() {
    return Mode.UNSORTED_FOR_UPDATE;
  }
}

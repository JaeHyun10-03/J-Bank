package com.jbank.concurrency;

import com.jbank.transfer.service.TransferLockStrategy.Mode;
import org.springframework.test.context.TestPropertySource;

/** 빈 값은 current(지금 서비스 방식)로 뜬다. 교착 0·실패 0. */
@TestPropertySource(properties = "jbank.transfer.lock-strategy=")
class CurrentLockStrategyScenarioTest extends TransferLockStrategyScenarioTest {

  @Override
  Mode expectedMode() {
    return Mode.CURRENT;
  }
}

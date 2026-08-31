package com.jbank.support.fds.domain;

/** 요구사항명세서 FR-SUP-003이 명시한 세 가지 간이 룰. */
public enum FdsRuleType {
  SINGLE_TRANSACTION_THRESHOLD_EXCEEDED,
  RAPID_REPEATED_TRANSFER,
  LATE_NIGHT_HIGH_VALUE_TRANSFER
}

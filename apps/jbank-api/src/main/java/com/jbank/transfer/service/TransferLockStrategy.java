package com.jbank.transfer.service;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 이체가 두 계좌를 잠그는 방식. 교착 재현 실험(docs/runbook/transfer-deadlock-experiment.md)에서만 바꾼다 — 기본값 current가 실제
 * 서비스 방식이고, 나머지 둘은 정렬·락 모드가 교착을 막는다는 것을 비교하려고 남긴 수정 전 방식이다.
 */
@Component
public class TransferLockStrategy {

  private static final Logger log = LoggerFactory.getLogger(TransferLockStrategy.class);

  public enum Mode {
    /** 계좌 순서로 정렬, 송금 FOR NO KEY UPDATE · 수신 FOR KEY SHARE(ADR 0012). */
    CURRENT("current"),
    /** 계좌 순서로 정렬, 둘 다 FOR UPDATE. */
    SORTED_FOR_UPDATE("sorted-for-update"),
    /** 요청 방향대로 송금 → 수신, 둘 다 FOR UPDATE. 양방향 동시 이체에서 교착이 난다. */
    UNSORTED_FOR_UPDATE("unsorted-for-update");

    private final String value;

    Mode(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }

  private final Mode mode;

  public TransferLockStrategy(@Value("${jbank.transfer.lock-strategy:current}") String value) {
    this.mode = parse(value);
    log.info("이체 락 전략: {}", mode.value());
    if (mode != Mode.CURRENT) {
      log.warn("이체 락 전략이 실험용 {}이다. 양방향 동시 이체에서 교착이 날 수 있다(기본 current).", mode.value());
    }
  }

  public Mode mode() {
    return mode;
  }

  static Mode parse(String value) {
    if (value == null || value.isBlank()) {
      return Mode.CURRENT;
    }
    String trimmed = value.trim();
    return Arrays.stream(Mode.values())
        .filter(m -> m.value.equals(trimmed))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "jbank.transfer.lock-strategy 값이 잘못됐다: "
                        + trimmed
                        + " (허용: "
                        + Arrays.stream(Mode.values())
                            .map(Mode::value)
                            .collect(Collectors.joining(", "))
                        + ")"));
  }
}

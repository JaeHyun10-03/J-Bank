package com.jbank.batch.fds;

import com.jbank.support.fds.domain.FdsRuleType;
import com.jbank.support.fds.domain.SuspiciousTransaction;
import com.jbank.support.fds.repository.SuspiciousTransactionRepository;
import com.jbank.transfer.domain.Transaction;
import com.jbank.transfer.domain.TransactionStatus;
import com.jbank.transfer.domain.TransactionType;
import com.jbank.transfer.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * 룰 기반 이상거래 탐지 간이 버전(구현계획 W7, 요구사항명세서 FR-SUP-003). CTR과 같은 결로
 * 하루치를 배치로 훑는다. 세 룰:
 *
 * <ol>
 *   <li>단일 거래 임계금액 초과
 *   <li>짧은 시간 내 반복 이체(같은 출금계좌에서 windowMinutes 이내 minRepeatCount건 이상)
 *   <li>심야 시간대(23시~06시) 고액 이체
 * </ol>
 *
 * 세 룰 다 "탐지해서 조회 가능하게 적재"까지만 한다 — 자동 차단·거래 취소는 하지 않는다
 * (실제 은행 FDS와 이 프로젝트 규모의 차이를 인프라아키텍처 문서 11절에 명시해뒀다).
 */
public class FdsDetectionTasklet implements Tasklet {

  private static final Logger log = LoggerFactory.getLogger(FdsDetectionTasklet.class);
  private static final int LATE_NIGHT_START_HOUR = 23;
  private static final int LATE_NIGHT_END_HOUR = 6;
  // "심야 시간대"는 국내 은행 영업 기준 KST 자정 전후를 뜻한다 — 컨테이너의 JVM 기본
  // 타임존(대개 UTC)에 따라 판정이 달라지면 안 되니 고정한다.
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final TransactionRepository transactionRepository;
  private final SuspiciousTransactionRepository suspiciousTransactionRepository;
  private final LocalDate runDate;
  private final BigDecimal singleTransactionThreshold;
  private final int rapidRepeatedWindowMinutes;
  private final int rapidRepeatedMinCount;
  private final BigDecimal lateNightThreshold;

  public FdsDetectionTasklet(
      TransactionRepository transactionRepository,
      SuspiciousTransactionRepository suspiciousTransactionRepository,
      LocalDate runDate,
      BigDecimal singleTransactionThreshold,
      int rapidRepeatedWindowMinutes,
      int rapidRepeatedMinCount,
      BigDecimal lateNightThreshold) {
    this.transactionRepository = transactionRepository;
    this.suspiciousTransactionRepository = suspiciousTransactionRepository;
    this.runDate = runDate;
    this.singleTransactionThreshold = singleTransactionThreshold;
    this.rapidRepeatedWindowMinutes = rapidRepeatedWindowMinutes;
    this.rapidRepeatedMinCount = rapidRepeatedMinCount;
    this.lateNightThreshold = lateNightThreshold;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    var startOfDay = runDate.atStartOfDay(KST).toOffsetDateTime();
    var endOfDay = runDate.plusDays(1).atStartOfDay(KST).toOffsetDateTime();

    List<Transaction> transfers =
        transactionRepository
            .findByTransactionTypeAndStatusAndProcessedAtBetweenOrderByFromAccountIdAscProcessedAtAsc(
                TransactionType.TRANSFER, TransactionStatus.COMPLETED, startOfDay, endOfDay);

    int flagged = 0;
    flagged += detectSingleThreshold(transfers);
    flagged += detectRapidRepeated(transfers);
    flagged += detectLateNightHighValue(transfers);

    log.info("이상거래 탐지 완료: 기준일={}, 대상 거래 {}건, 신규 적재 {}건", runDate, transfers.size(), flagged);
    return RepeatStatus.FINISHED;
  }

  private int detectSingleThreshold(List<Transaction> transfers) {
    int count = 0;
    for (Transaction transaction : transfers) {
      if (transaction.getAmount().compareTo(singleTransactionThreshold) >= 0) {
        count +=
            flagIfAbsent(
                transaction,
                FdsRuleType.SINGLE_TRANSACTION_THRESHOLD_EXCEEDED,
                "단일 거래 금액 " + transaction.getAmount() + "원이 임계금액을 초과");
      }
    }
    return count;
  }

  private int detectLateNightHighValue(List<Transaction> transfers) {
    int count = 0;
    for (Transaction transaction : transfers) {
      int hour = transaction.getProcessedAt().atZoneSameInstant(KST).getHour();
      boolean isLateNight = hour >= LATE_NIGHT_START_HOUR || hour < LATE_NIGHT_END_HOUR;
      if (isLateNight && transaction.getAmount().compareTo(lateNightThreshold) >= 0) {
        count +=
            flagIfAbsent(
                transaction,
                FdsRuleType.LATE_NIGHT_HIGH_VALUE_TRANSFER,
                "심야 시간대(" + hour + "시) 고액 이체 " + transaction.getAmount() + "원");
      }
    }
    return count;
  }

  // findByTransactionTypeAndStatusAndProcessedAtBetweenOrderByFromAccountIdAscProcessedAtAsc이
  // 이미 (fromAccountId, processedAt) 순으로 정렬해 왔으니, 계좌가 바뀌는 지점마다 슬라이딩
  // 윈도우를 리셋하면서 한 번의 순회로 계좌별 그룹을 처리할 수 있다.
  private int detectRapidRepeated(List<Transaction> transfers) {
    int count = 0;
    List<Transaction> window = new ArrayList<>();
    Long currentAccountId = null;

    for (Transaction transaction : transfers) {
      if (!transaction.getFromAccountId().equals(currentAccountId)) {
        currentAccountId = transaction.getFromAccountId();
        window.clear();
      }
      window.add(transaction);
      // 윈도우 시작에서 너무 오래된 것들을 밀어낸다.
      while (Duration.between(window.get(0).getProcessedAt(), transaction.getProcessedAt())
          .toMinutes() > rapidRepeatedWindowMinutes) {
        window.remove(0);
      }
      if (window.size() >= rapidRepeatedMinCount) {
        count +=
            flagIfAbsent(
                transaction,
                FdsRuleType.RAPID_REPEATED_TRANSFER,
                rapidRepeatedWindowMinutes + "분 이내 " + window.size() + "건 반복 이체");
      }
    }
    return count;
  }

  private int flagIfAbsent(Transaction transaction, FdsRuleType ruleType, String detail) {
    if (suspiciousTransactionRepository.existsByTransactionIdAndRuleType(
        transaction.getTransactionId(), ruleType)) {
      return 0;
    }
    suspiciousTransactionRepository.save(
        new SuspiciousTransaction(
            transaction.getTransactionId(),
            transaction.getFromAccountId(),
            ruleType,
            transaction.getAmount(),
            detail,
            transaction.getProcessedAt()));
    return 1;
  }
}

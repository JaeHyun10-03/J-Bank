package com.jbank.batch.ctr;

import com.jbank.account.domain.Account;
import com.jbank.account.repository.AccountRepository;
import com.jbank.support.ctr.domain.CtrReportQueue;
import com.jbank.support.ctr.repository.CtrReportQueueRepository;
import com.jbank.transfer.repository.AccountCashTotal;
import com.jbank.transfer.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * 일자별·고객별 현금성 거래(입금·출금) 합계가 기준 금액을 넘으면 CtrReportQueue에 적재하고, 실제 보고 전송 대신 로그로 남긴다(구현계획 W5, 요구사항명세서
 * FR-SUP-004·7.1절).
 */
public class CtrDetectionTasklet implements Tasklet {

  private static final Logger log = LoggerFactory.getLogger(CtrDetectionTasklet.class);

  private final TransactionRepository transactionRepository;
  private final AccountRepository accountRepository;
  private final CtrReportQueueRepository ctrReportQueueRepository;
  private final LocalDate runDate;
  private final BigDecimal thresholdAmount;

  public CtrDetectionTasklet(
      TransactionRepository transactionRepository,
      AccountRepository accountRepository,
      CtrReportQueueRepository ctrReportQueueRepository,
      LocalDate runDate,
      BigDecimal thresholdAmount) {
    this.transactionRepository = transactionRepository;
    this.accountRepository = accountRepository;
    this.ctrReportQueueRepository = ctrReportQueueRepository;
    this.runDate = runDate;
    this.thresholdAmount = thresholdAmount;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    var startOfDay = runDate.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
    var endOfDay = runDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();

    List<AccountCashTotal> accountTotals =
        transactionRepository.sumCashByAccount(startOfDay, endOfDay);
    if (accountTotals.isEmpty()) {
      log.info("고액현금거래 판별 완료: 기준일={}, 대상 없음", runDate);
      return RepeatStatus.FINISHED;
    }

    Map<Long, Account> accountsById =
        accountRepository
            .findAllById(accountTotals.stream().map(AccountCashTotal::accountId).toList())
            .stream()
            .collect(Collectors.toMap(Account::getAccountId, account -> account));

    Map<Long, List<AccountCashTotal>> totalsByCustomer =
        accountTotals.stream()
            .collect(
                Collectors.groupingBy(
                    total -> accountsById.get(total.accountId()).getCustomerId()));

    int queuedCount = 0;
    for (Map.Entry<Long, List<AccountCashTotal>> entry : totalsByCustomer.entrySet()) {
      Long customerId = entry.getKey();
      List<AccountCashTotal> customerAccountTotals = entry.getValue();
      BigDecimal customerTotal =
          customerAccountTotals.stream()
              .map(AccountCashTotal::totalAmount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      if (customerTotal.compareTo(thresholdAmount) < 0) {
        continue;
      }

      for (AccountCashTotal accountTotal : customerAccountTotals) {
        queuedCount += queueIfAbsent(customerId, accountTotal);
      }
    }

    log.info("고액현금거래 판별 완료: 기준일={}, 신규 적재 {}건", runDate, queuedCount);
    return RepeatStatus.FINISHED;
  }

  private int queueIfAbsent(Long customerId, AccountCashTotal accountTotal) {
    Long accountId = accountTotal.accountId();
    if (ctrReportQueueRepository.existsByCustomerIdAndAccountIdAndTransactionDate(
        customerId, accountId, runDate)) {
      return 0;
    }

    CtrReportQueue report =
        new CtrReportQueue(customerId, accountId, runDate, accountTotal.totalAmount());
    report = ctrReportQueueRepository.save(report);

    // 실제 KoFIU 전산망 전송은 감독당국 승인이 필요해 불가능하므로 로그 출력으로 대체한다(요구사항명세서 7.1절).
    log.info(
        "고액현금거래 보고대상 전송(목업): reportId={}, customerId={}, accountId={}, 기준일={}, 합계={}",
        report.getReportId(),
        customerId,
        accountId,
        runDate,
        accountTotal.totalAmount());
    report.markLogged();
    ctrReportQueueRepository.save(report);
    return 1;
  }
}

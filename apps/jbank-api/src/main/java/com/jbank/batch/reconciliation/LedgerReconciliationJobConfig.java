package com.jbank.batch.reconciliation;

import com.jbank.account.repository.AccountRepository;
import com.jbank.batch.lock.SingleInstanceJobExecutionListener;
import com.jbank.ledger.repository.LedgerEntryRepository;
import com.jbank.transfer.repository.PendingCreditRepository;
import org.redisson.api.RedissonClient;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

/** 원장 정합성 대사 배치(구현계획 W5). 집계 쿼리로 판정만 하는 잡이라 청크가 아닌 Tasklet 스텝 하나로 구성한다. */
@Configuration
public class LedgerReconciliationJobConfig {

  @Bean
  public Job ledgerReconciliationJob(
      JobRepository jobRepository, Step ledgerReconciliationStep, RedissonClient redissonClient) {
    return new JobBuilder("ledgerReconciliationJob", jobRepository)
        .start(ledgerReconciliationStep)
        .listener(new SingleInstanceJobExecutionListener(redissonClient))
        .build();
  }

  @Bean
  public Step ledgerReconciliationStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      AccountRepository accountRepository,
      LedgerEntryRepository ledgerEntryRepository,
      PendingCreditRepository pendingCreditRepository) {
    // 여러 집계 쿼리가 같은 시점을 보게 한다 — 사이에 입금 반영이 끼면 차변/대변·잔액 비교가 오탐한다.
    DefaultTransactionAttribute snapshot = new DefaultTransactionAttribute();
    snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    return new StepBuilder("ledgerReconciliationStep", jobRepository)
        .tasklet(
            new LedgerReconciliationTasklet(
                accountRepository, ledgerEntryRepository, pendingCreditRepository),
            transactionManager)
        .transactionAttribute(snapshot)
        .build();
  }
}

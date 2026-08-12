package com.jbank.batch.reconciliation;

import com.jbank.account.repository.AccountRepository;
import com.jbank.ledger.repository.LedgerEntryRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** 원장 정합성 대사 배치(구현계획 W5). 집계 쿼리로 판정만 하는 잡이라 청크가 아닌 Tasklet 스텝 하나로 구성한다. */
@Configuration
public class LedgerReconciliationJobConfig {

  @Bean
  public Job ledgerReconciliationJob(JobRepository jobRepository, Step ledgerReconciliationStep) {
    return new JobBuilder("ledgerReconciliationJob", jobRepository)
        .start(ledgerReconciliationStep)
        .build();
  }

  @Bean
  public Step ledgerReconciliationStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      AccountRepository accountRepository,
      LedgerEntryRepository ledgerEntryRepository) {
    return new StepBuilder("ledgerReconciliationStep", jobRepository)
        .tasklet(
            new LedgerReconciliationTasklet(accountRepository, ledgerEntryRepository),
            transactionManager)
        .build();
  }
}

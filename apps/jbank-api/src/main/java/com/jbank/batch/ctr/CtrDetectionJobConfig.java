package com.jbank.batch.ctr;

import com.jbank.account.repository.AccountRepository;
import com.jbank.batch.lock.SingleInstanceJobExecutionListener;
import com.jbank.support.ctr.repository.CtrReportQueueRepository;
import com.jbank.transfer.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.redisson.api.RedissonClient;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** 고액현금거래(CTR) 판별 배치(구현계획 W5). 집계-판별-적재만 하는 잡이라 Tasklet 스텝 하나로 구성한다. */
@Configuration
public class CtrDetectionJobConfig {

  @Bean
  public Job ctrDetectionJob(
      JobRepository jobRepository, Step ctrDetectionStep, RedissonClient redissonClient) {
    return new JobBuilder("ctrDetectionJob", jobRepository)
        .start(ctrDetectionStep)
        .listener(new SingleInstanceJobExecutionListener(redissonClient))
        .build();
  }

  @Bean
  public Step ctrDetectionStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      CtrDetectionTasklet ctrDetectionTasklet) {
    return new StepBuilder("ctrDetectionStep", jobRepository)
        .tasklet(ctrDetectionTasklet, transactionManager)
        .build();
  }

  @Bean
  @StepScope
  public CtrDetectionTasklet ctrDetectionTasklet(
      TransactionRepository transactionRepository,
      AccountRepository accountRepository,
      CtrReportQueueRepository ctrReportQueueRepository,
      @Value("#{jobParameters['runDate']}") String runDate,
      @Value("${jbank.batch.ctr.threshold-amount:10000000}") String thresholdAmount) {
    return new CtrDetectionTasklet(
        transactionRepository,
        accountRepository,
        ctrReportQueueRepository,
        LocalDate.parse(runDate),
        new BigDecimal(thresholdAmount));
  }
}

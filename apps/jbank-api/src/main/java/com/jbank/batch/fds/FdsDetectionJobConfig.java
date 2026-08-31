package com.jbank.batch.fds;

import com.jbank.batch.lock.SingleInstanceJobExecutionListener;
import com.jbank.support.fds.repository.SuspiciousTransactionRepository;
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

/** 이상거래 탐지 배치(구현계획 W7, FR-SUP-003). 집계·판별·적재만 하는 잡이라 CTR과 같이 Tasklet 스텝 하나로 구성한다. */
@Configuration
public class FdsDetectionJobConfig {

  @Bean
  public Job fdsDetectionJob(
      JobRepository jobRepository, Step fdsDetectionStep, RedissonClient redissonClient) {
    return new JobBuilder("fdsDetectionJob", jobRepository)
        .start(fdsDetectionStep)
        .listener(new SingleInstanceJobExecutionListener(redissonClient))
        .build();
  }

  @Bean
  public Step fdsDetectionStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FdsDetectionTasklet fdsDetectionTasklet) {
    return new StepBuilder("fdsDetectionStep", jobRepository)
        .tasklet(fdsDetectionTasklet, transactionManager)
        .build();
  }

  @Bean
  @StepScope
  public FdsDetectionTasklet fdsDetectionTasklet(
      TransactionRepository transactionRepository,
      SuspiciousTransactionRepository suspiciousTransactionRepository,
      @Value("#{jobParameters['runDate']}") String runDate,
      @Value("${jbank.batch.fds.single-transaction-threshold:5000000}")
          String singleTransactionThreshold,
      @Value("${jbank.batch.fds.rapid-repeated.window-minutes:5}") int rapidRepeatedWindowMinutes,
      @Value("${jbank.batch.fds.rapid-repeated.min-count:3}") int rapidRepeatedMinCount,
      @Value("${jbank.batch.fds.late-night.threshold:3000000}") String lateNightThreshold) {
    return new FdsDetectionTasklet(
        transactionRepository,
        suspiciousTransactionRepository,
        LocalDate.parse(runDate),
        new BigDecimal(singleTransactionThreshold),
        rapidRepeatedWindowMinutes,
        rapidRepeatedMinCount,
        new BigDecimal(lateNightThreshold));
  }
}

package com.jbank.batch.interest;

import com.jbank.batch.lock.SingleInstanceJobExecutionListener;
import java.time.LocalDate;
import org.redisson.api.RedissonClient;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 만기 도래 계약에 이자를 지급하고 계약을 만기 상태로 전환한다(구현계획 W5). 청크 크기는 W6에서 측정 후 조정.
 *
 * <p>W7에서 product 모듈을 별도 배포 단위로 떼어내면서 만기 계약 조회·이자율 계산은
 * jbank-product의 내부 API({@link MaturedContractApiClient})로 옮겨갔다 — 더는 같은 JVM
 * 안에서 {@code ProductContractRepository}를 직접 못 읽는다. 이자를 실제로 입금하는
 * 계좌·거래·원장은 여전히 이 서비스가 소유하므로, 돈이 움직이는 부분은 그대로 로컬
 * 트랜잭션 안에서 처리한다(docs/adr/0007-w7-product-module-separation.md).
 */
@Configuration
public class InterestMaturityJobConfig {

  private static final int CHUNK_SIZE = 100;

  @Bean
  public Job interestMaturityJob(
      JobRepository jobRepository, Step interestMaturityStep, RedissonClient redissonClient) {
    return new JobBuilder("interestMaturityJob", jobRepository)
        .start(interestMaturityStep)
        .listener(new SingleInstanceJobExecutionListener(redissonClient))
        .build();
  }

  @Bean
  public Step interestMaturityStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      ItemReader<MaturedContractDto> maturedContractItemReader,
      ItemWriter<MaturedContractDto> maturedContractInterestItemWriter) {
    return new StepBuilder("interestMaturityStep", jobRepository)
        .<MaturedContractDto, MaturedContractDto>chunk(CHUNK_SIZE, transactionManager)
        .reader(maturedContractItemReader)
        .writer(maturedContractInterestItemWriter)
        .build();
  }

  @Bean
  @StepScope
  public ItemReader<MaturedContractDto> maturedContractItemReader(
      MaturedContractApiClient maturedContractApiClient,
      @Value("#{jobParameters['runDate']}") String runDate) {
    return new ListItemReader<>(maturedContractApiClient.findMatured(LocalDate.parse(runDate)));
  }
}

package com.jbank.batch.interest;

import com.jbank.batch.lock.SingleInstanceJobExecutionListener;
import com.jbank.product.domain.ContractStatus;
import com.jbank.product.domain.ProductContract;
import com.jbank.product.repository.ProductContractRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.redisson.api.RedissonClient;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

/** 만기 도래 계약에 이자를 지급하고 계약을 만기 상태로 전환한다(구현계획 W5). 청크 크기는 W6에서 측정 후 조정. */
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
      RepositoryItemReader<ProductContract> maturedContractItemReader,
      ItemProcessor<ProductContract, MaturedContractInterest> maturedContractInterestItemProcessor,
      ItemWriter<MaturedContractInterest> maturedContractInterestItemWriter) {
    return new StepBuilder("interestMaturityStep", jobRepository)
        .<ProductContract, MaturedContractInterest>chunk(CHUNK_SIZE, transactionManager)
        .reader(maturedContractItemReader)
        .processor(maturedContractInterestItemProcessor)
        .writer(maturedContractInterestItemWriter)
        .build();
  }

  @Bean
  @StepScope
  public RepositoryItemReader<ProductContract> maturedContractItemReader(
      ProductContractRepository productContractRepository,
      @Value("#{jobParameters['runDate']}") String runDate) {
    // 기준일 당일 만기까지 포함하려고 다음날 자정을 배타적 상한으로 쓴다.
    var exclusiveUpperBound =
        LocalDate.parse(runDate)
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toOffsetDateTime();

    RepositoryItemReader<ProductContract> reader = new RepositoryItemReader<>();
    reader.setRepository(productContractRepository);
    reader.setMethodName("findByStatusAndMaturityAtLessThan");
    reader.setArguments(List.of(ContractStatus.ACTIVE, exclusiveUpperBound));
    reader.setSort(Map.of("contractId", Sort.Direction.ASC));
    reader.setPageSize(CHUNK_SIZE);
    reader.setName("maturedContractItemReader");
    return reader;
  }
}

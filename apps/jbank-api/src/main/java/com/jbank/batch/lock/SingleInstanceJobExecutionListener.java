package com.jbank.batch.lock;

import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/**
 * 배치 잡 단일 실행을 보장하는 리스너(구현계획 W7). K8s CronJob의
 * concurrencyPolicy: Forbid가 평상시 중복 실행을 막아주지만, 인스턴스를 여러
 * 개로 늘리는 시점부터는 수동 재실행(kubectl create job --from=cronjob)
 * 같은 경로로 같은 잡이 동시에 시작될 여지가 생긴다(ADR 0003). 잡 시작 전
 * 락을 못 잡으면 스텝을 하나도 실행하지 않고 그대로 실패시킨다 —
 * beforeJob에서 예외를 던지면 AbstractJob#execute가 스텝 실행 전에 잡을
 * FAILED로 종료하는 Spring Batch 표준 동작에 기댄다.
 */
public class SingleInstanceJobExecutionListener implements JobExecutionListener {

  private static final Logger log = LoggerFactory.getLogger(SingleInstanceJobExecutionListener.class);

  // ponytail: 배치 잡 최대 실행 시간을 못 미더워 고정값으로 넉넉히 잡음.
  // 잡별로 실제 실행 시간이 이 값을 넘으면 워치독 갱신 없이 락이 먼저
  // 풀려버리니, 잡이 실제로 30분을 넘기게 되면 잡별 leaseTime으로 분리할 것.
  private static final long LEASE_SECONDS = 30 * 60;

  private final RedissonClient redissonClient;
  private RLock lock;

  public SingleInstanceJobExecutionListener(RedissonClient redissonClient) {
    this.redissonClient = redissonClient;
  }

  @Override
  public void beforeJob(JobExecution jobExecution) {
    String jobName = jobExecution.getJobInstance().getJobName();
    lock = redissonClient.getLock("batch-job-lock:" + jobName);
    boolean acquired;
    try {
      acquired = lock.tryLock(0, LEASE_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("잡 락 획득 중 인터럽트: " + jobName, e);
    }
    if (!acquired) {
      throw new IllegalStateException("다른 인스턴스가 이미 실행 중인 잡이라 건너뜀: " + jobName);
    }
    log.info("배치 잡 단일 실행 락 획득: {}", jobName);
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    if (lock != null && lock.isHeldByCurrentThread()) {
      lock.unlock();
    }
  }
}

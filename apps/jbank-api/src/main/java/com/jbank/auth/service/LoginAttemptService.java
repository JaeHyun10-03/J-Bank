package com.jbank.auth.service;

import java.time.Duration;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/** 로그인 연속 실패 5회 초과 시 30분 잠금(API설계 4절, FR-AUTH-001). */
@Component
public class LoginAttemptService {

  private static final String KEY_PREFIX = "auth:login-failures:";
  private static final int MAX_ATTEMPTS = 5;
  private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

  private final RedissonClient redissonClient;

  public LoginAttemptService(RedissonClient redissonClient) {
    this.redissonClient = redissonClient;
  }

  public boolean isLocked(String loginId) {
    RAtomicLong counter = redissonClient.getAtomicLong(KEY_PREFIX + loginId);
    return counter.isExists() && counter.get() >= MAX_ATTEMPTS;
  }

  public void recordFailure(String loginId) {
    RAtomicLong counter = redissonClient.getAtomicLong(KEY_PREFIX + loginId);
    long count = counter.incrementAndGet();
    if (count == 1) {
      counter.expire(LOCK_DURATION);
    }
  }

  public void reset(String loginId) {
    redissonClient.getAtomicLong(KEY_PREFIX + loginId).delete();
  }
}

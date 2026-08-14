package com.jbank.transfer.service;

import java.security.SecureRandom;
import java.time.Duration;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 임계금액 초과 이체의 2차 인증(FR-AUTH-003)에 쓰는 OTP를 Redis에 저장·검증한다. 실제 SMS 발송은 CTR 배치와 같은 원칙으로 로그
 * 출력으로 대체한다.
 */
@Component
public class OtpService {

  private static final Logger log = LoggerFactory.getLogger(OtpService.class);
  private static final String CODE_KEY_PREFIX = "transfer:otp:";
  private static final String FAILURE_KEY_PREFIX = "transfer:otp-failures:";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final RedissonClient redissonClient;
  private final Duration expiry;
  private final int maxAttempts;

  public OtpService(
      RedissonClient redissonClient,
      @Value("${jbank.transfer.otp.expiry-minutes:3}") long expiryMinutes,
      @Value("${jbank.transfer.otp.max-attempts:5}") int maxAttempts) {
    this.redissonClient = redissonClient;
    this.expiry = Duration.ofMinutes(expiryMinutes);
    this.maxAttempts = maxAttempts;
  }

  public String issue(Long transactionId) {
    String code = generateCode();
    redissonClient.getBucket(codeKey(transactionId)).set(code, expiry);
    redissonClient.getAtomicLong(failureKey(transactionId)).delete();
    log.info("OTP 발급: transactionId={}, code={}", transactionId, code);
    return code;
  }

  public VerifyResult verify(Long transactionId, String inputCode) {
    RBucket<String> bucket = redissonClient.getBucket(codeKey(transactionId));
    String stored = bucket.get();
    if (stored == null) {
      return VerifyResult.EXPIRED;
    }
    if (stored.equals(inputCode)) {
      bucket.delete();
      redissonClient.getAtomicLong(failureKey(transactionId)).delete();
      return VerifyResult.MATCH;
    }

    RAtomicLong failures = redissonClient.getAtomicLong(failureKey(transactionId));
    long count = failures.incrementAndGet();
    if (count == 1) {
      failures.expire(expiry);
    }
    return count >= maxAttempts ? VerifyResult.LIMIT_EXCEEDED : VerifyResult.MISMATCH;
  }

  private String generateCode() {
    return String.format("%06d", RANDOM.nextInt(1_000_000));
  }

  private String codeKey(Long transactionId) {
    return CODE_KEY_PREFIX + transactionId;
  }

  private String failureKey(Long transactionId) {
    return FAILURE_KEY_PREFIX + transactionId;
  }

  public enum VerifyResult {
    MATCH,
    MISMATCH,
    LIMIT_EXCEEDED,
    EXPIRED
  }
}

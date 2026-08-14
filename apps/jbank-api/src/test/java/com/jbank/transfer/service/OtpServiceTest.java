package com.jbank.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

class OtpServiceTest {

  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
  static final AtomicLong TRANSACTION_ID_SEQUENCE = new AtomicLong();

  static RedissonClient redissonClient;

  @BeforeAll
  static void startRedis() {
    REDIS.start();
    Config config = new Config();
    config
        .useSingleServer()
        .setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
    redissonClient = Redisson.create(config);
  }

  @AfterAll
  static void shutdown() {
    redissonClient.shutdown();
    REDIS.stop();
  }

  private final OtpService otpService = new OtpService(redissonClient, 3, 3);

  @Test
  void 발급한_코드로_검증하면_MATCH를_반환한다() {
    Long transactionId = nextTransactionId();
    String code = otpService.issue(transactionId);

    assertThat(otpService.verify(transactionId, code)).isEqualTo(OtpService.VerifyResult.MATCH);
  }

  @Test
  void 발급한_적_없는_거래는_EXPIRED를_반환한다() {
    Long transactionId = nextTransactionId();

    assertThat(otpService.verify(transactionId, "000000"))
        .isEqualTo(OtpService.VerifyResult.EXPIRED);
  }

  @Test
  void 검증에_성공하면_코드가_삭제되어_재사용할_수_없다() {
    Long transactionId = nextTransactionId();
    String code = otpService.issue(transactionId);
    otpService.verify(transactionId, code);

    assertThat(otpService.verify(transactionId, code)).isEqualTo(OtpService.VerifyResult.EXPIRED);
  }

  @Test
  void 한도_미만의_불일치는_MISMATCH를_반환한다() {
    Long transactionId = nextTransactionId();
    otpService.issue(transactionId);

    assertThat(otpService.verify(transactionId, "wrong-1"))
        .isEqualTo(OtpService.VerifyResult.MISMATCH);
    assertThat(otpService.verify(transactionId, "wrong-2"))
        .isEqualTo(OtpService.VerifyResult.MISMATCH);
  }

  @Test
  void 실패_횟수가_한도에_도달하면_LIMIT_EXCEEDED를_반환한다() {
    Long transactionId = nextTransactionId();
    otpService.issue(transactionId);

    otpService.verify(transactionId, "wrong-1");
    otpService.verify(transactionId, "wrong-2");
    OtpService.VerifyResult third = otpService.verify(transactionId, "wrong-3");

    assertThat(third).isEqualTo(OtpService.VerifyResult.LIMIT_EXCEEDED);
  }

  private Long nextTransactionId() {
    return TRANSACTION_ID_SEQUENCE.incrementAndGet();
  }
}

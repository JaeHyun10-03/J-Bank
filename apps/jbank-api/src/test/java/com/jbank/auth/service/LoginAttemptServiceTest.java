package com.jbank.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

class LoginAttemptServiceTest {

  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

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

  private final LoginAttemptService service = new LoginAttemptService(redissonClient);

  @Test
  void 실패_4회까지는_잠기지_않는다() {
    String loginId = "user-" + UUID.randomUUID();
    for (int i = 0; i < 4; i++) {
      service.recordFailure(loginId);
    }

    assertThat(service.isLocked(loginId)).isFalse();
  }

  @Test
  void 실패_5회면_잠긴다() {
    String loginId = "user-" + UUID.randomUUID();
    for (int i = 0; i < 5; i++) {
      service.recordFailure(loginId);
    }

    assertThat(service.isLocked(loginId)).isTrue();
  }

  @Test
  void 성공하면_실패횟수가_초기화된다() {
    String loginId = "user-" + UUID.randomUUID();
    for (int i = 0; i < 5; i++) {
      service.recordFailure(loginId);
    }

    service.reset(loginId);

    assertThat(service.isLocked(loginId)).isFalse();
  }
}

package com.jbank.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbank.auth.AuthException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

/**
 * 실제 Redis로 로테이션·재사용 탐지를 검증한다. RedissonClient는 인터페이스가 커서 목으로는 이 로직(원자적 존재 확인 + 삭제 + 집합 관리)의 정확성을
 * 신뢰성 있게 확인하기 어렵다.
 */
class RefreshTokenStoreTest {

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

  private final JwtTokenProvider jwtTokenProvider =
      new JwtTokenProvider("test-secret-key-at-least-32-bytes-long-for-hs256");
  private final RefreshTokenStore store = new RefreshTokenStore(redissonClient, jwtTokenProvider);

  @Test
  void 발급한_jti로_로테이션하면_새_jti를_돌려주고_기존_jti는_무효화된다() {
    Long customerId = 1L;
    String jti = store.issue(customerId);

    String newJti = store.rotate(customerId, jti);

    assertThat(newJti).isNotEqualTo(jti);
    assertThatThrownBy(() -> store.rotate(customerId, jti)).isInstanceOf(AuthException.class);
  }

  @Test
  void 이미_소비된_jti가_다시_들어오면_같은_고객의_다른_유효_jti도_전부_폐기된다() {
    Long customerId = 2L;
    String jti1 = store.issue(customerId);
    String jti2 = store.issue(customerId);
    String rotatedJti1 = store.rotate(customerId, jti1);

    // jti1은 이미 로테이션으로 소비됨 — 재사용 시도
    assertThatThrownBy(() -> store.rotate(customerId, jti1)).isInstanceOf(AuthException.class);

    // 재사용 탐지로 jti2, rotatedJti1도 전부 폐기되어야 한다
    assertThatThrownBy(() -> store.rotate(customerId, jti2)).isInstanceOf(AuthException.class);
    assertThatThrownBy(() -> store.rotate(customerId, rotatedJti1))
        .isInstanceOf(AuthException.class);
  }

  @Test
  void 다른_고객의_jti로_로테이션을_시도하면_거절한다() {
    Long customerId = 3L;
    Long attackerCustomerId = 4L;
    String jti = store.issue(customerId);

    assertThatThrownBy(() -> store.rotate(attackerCustomerId, jti))
        .isInstanceOf(AuthException.class);
  }
}

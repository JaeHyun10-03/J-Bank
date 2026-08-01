package com.jbank.auth.jwt;

import com.jbank.auth.AuthException;
import com.jbank.global.exception.ErrorCode;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 갱신 토큰 화이트리스트. jti(토큰 식별자)를 Redis에 저장해두고, 재발급마다 로테이션한다. 이미 소비되었거나 알 수 없는 jti가 다시 들어오면 탈취를 의심할 근거로
 * 보아 그 고객의 갱신 토큰 계열 전체를 폐기한다(API설계 2.2절).
 */
@Component
public class RefreshTokenStore {

  private static final String TOKEN_KEY_PREFIX = "auth:refresh:token:";
  private static final String FAMILY_KEY_PREFIX = "auth:refresh:family:";

  private final RedissonClient redissonClient;
  private final JwtTokenProvider jwtTokenProvider;

  public RefreshTokenStore(RedissonClient redissonClient, JwtTokenProvider jwtTokenProvider) {
    this.redissonClient = redissonClient;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  public String issue(Long customerId) {
    String jti = UUID.randomUUID().toString();
    Duration ttl = jwtTokenProvider.getRefreshTokenTtl();
    redissonClient.<String>getBucket(TOKEN_KEY_PREFIX + jti).set(String.valueOf(customerId), ttl);
    RSet<String> family = redissonClient.getSet(FAMILY_KEY_PREFIX + customerId);
    family.add(jti);
    family.expire(ttl);
    return jti;
  }

  /** 유효하면 기존 jti를 폐기하고 새 jti를 발급한다. 유효하지 않으면 계열 전체를 폐기하고 예외를 던진다. */
  public String rotate(Long customerId, String jti) {
    String key = TOKEN_KEY_PREFIX + jti;
    String storedCustomerId = redissonClient.<String>getBucket(key).get();
    if (storedCustomerId == null || !storedCustomerId.equals(String.valueOf(customerId))) {
      revokeAll(customerId);
      throw new AuthException(ErrorCode.AUTH_003_REFRESH_TOKEN_INVALID);
    }
    redissonClient.getBucket(key).delete();
    redissonClient.getSet(FAMILY_KEY_PREFIX + customerId).remove(jti);
    return issue(customerId);
  }

  public void revokeAll(Long customerId) {
    RSet<String> family = redissonClient.getSet(FAMILY_KEY_PREFIX + customerId);
    Set<String> jtis = family.readAll();
    for (String jti : jtis) {
      redissonClient.getBucket(TOKEN_KEY_PREFIX + jti).delete();
    }
    family.delete();
  }
}

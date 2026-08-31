package com.jbank.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * jbank-api가 발급한 인증 토큰을 검증만 한다 — jbank-product는 로그인 흐름이 없어
 * 토큰을 발급하지 않는다. {@code jbank.jwt.secret} 값은 두 서비스가 같은 Secret에서
 * 받아 공유한다(발급자와 검증자가 서명 키를 공유하는 건 JWT의 표준 사용법).
 */
@Component
public class JwtTokenProvider {

  private final SecretKey key;

  public JwtTokenProvider(@Value("${jbank.jwt.secret}") String secret) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  /** 서명·만료 검증에 실패하면 빈 값을 반환한다. 위조/만료를 구분해서 처리할 필요가 없다. */
  public Optional<Claims> parse(String token) {
    try {
      return Optional.of(
          Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}

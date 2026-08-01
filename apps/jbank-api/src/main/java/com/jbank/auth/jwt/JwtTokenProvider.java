package com.jbank.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** API설계 2.2절 — 인증 토큰 15분, 갱신 토큰 14일. */
@Component
public class JwtTokenProvider {

  private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
  private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);

  private final SecretKey key;

  public JwtTokenProvider(@Value("${jbank.jwt.secret}") String secret) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public Duration getAccessTokenTtl() {
    return ACCESS_TOKEN_TTL;
  }

  public Duration getRefreshTokenTtl() {
    return REFRESH_TOKEN_TTL;
  }

  public String createAccessToken(Long customerId) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(customerId))
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ACCESS_TOKEN_TTL)))
        .signWith(key)
        .compact();
  }

  public String createRefreshToken(Long customerId, String jti) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(customerId))
        .id(jti)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(REFRESH_TOKEN_TTL)))
        .signWith(key)
        .compact();
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

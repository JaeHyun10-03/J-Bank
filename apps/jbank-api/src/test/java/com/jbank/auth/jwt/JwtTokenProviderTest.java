package com.jbank.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private final JwtTokenProvider provider =
      new JwtTokenProvider("test-secret-key-at-least-32-bytes-long-for-hs256");

  @Test
  void 발급한_인증토큰을_파싱하면_고객ID가_그대로_나온다() {
    String token = provider.createAccessToken(42L);

    Optional<Claims> claims = provider.parse(token);

    assertThat(claims).isPresent();
    assertThat(claims.get().getSubject()).isEqualTo("42");
  }

  @Test
  void 발급한_갱신토큰을_파싱하면_jti가_그대로_나온다() {
    String token = provider.createRefreshToken(42L, "jti-1234");

    Optional<Claims> claims = provider.parse(token);

    assertThat(claims).isPresent();
    assertThat(claims.get().getSubject()).isEqualTo("42");
    assertThat(claims.get().getId()).isEqualTo("jti-1234");
  }

  @Test
  void 위조된_토큰은_파싱에_실패한다() {
    String token = provider.createAccessToken(42L);
    String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

    Optional<Claims> claims = provider.parse(tampered);

    assertThat(claims).isEmpty();
  }

  @Test
  void 다른_키로_서명된_토큰은_파싱에_실패한다() {
    JwtTokenProvider other = new JwtTokenProvider("different-secret-key-at-least-32-bytes-long");
    String token = other.createAccessToken(42L);

    Optional<Claims> claims = provider.parse(token);

    assertThat(claims).isEmpty();
  }
}

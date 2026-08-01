package com.jbank.auth.jwt;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** API설계 2.2절의 쿠키 3종. XSRF-TOKEN만 HttpOnly를 뺀다 — 클라이언트가 읽어 헤더에 복사해야 한다. */
@Component
public class AuthCookieFactory {

  private final JwtTokenProvider jwtTokenProvider;

  public AuthCookieFactory(JwtTokenProvider jwtTokenProvider) {
    this.jwtTokenProvider = jwtTokenProvider;
  }

  public ResponseCookie accessTokenCookie(String token) {
    return build("access_token", token, true, jwtTokenProvider.getAccessTokenTtl().getSeconds());
  }

  public ResponseCookie refreshTokenCookie(String token) {
    return build("refresh_token", token, true, jwtTokenProvider.getRefreshTokenTtl().getSeconds());
  }

  public ResponseCookie csrfTokenCookie(String token) {
    return build("XSRF-TOKEN", token, false, jwtTokenProvider.getRefreshTokenTtl().getSeconds());
  }

  public ResponseCookie clearedAccessTokenCookie() {
    return build("access_token", "", true, 0);
  }

  public ResponseCookie clearedRefreshTokenCookie() {
    return build("refresh_token", "", true, 0);
  }

  public ResponseCookie clearedCsrfTokenCookie() {
    return build("XSRF-TOKEN", "", false, 0);
  }

  private ResponseCookie build(String name, String value, boolean httpOnly, long maxAgeSeconds) {
    return ResponseCookie.from(name, value)
        .httpOnly(httpOnly)
        .secure(true)
        .sameSite("Lax")
        .path("/")
        .maxAge(maxAgeSeconds)
        .build();
  }
}

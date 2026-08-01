package com.jbank.auth.controller;

import com.jbank.auth.AuthException;
import com.jbank.auth.dto.LoginRequest;
import com.jbank.auth.dto.LoginResponse;
import com.jbank.auth.dto.RefreshResponse;
import com.jbank.auth.jwt.AuthCookieFactory;
import com.jbank.auth.service.AuthResult;
import com.jbank.auth.service.AuthService;
import com.jbank.global.exception.ErrorCode;
import com.jbank.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "로그인·토큰 재발급·로그아웃 API")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final AuthCookieFactory cookieFactory;

  public AuthController(AuthService authService, AuthCookieFactory cookieFactory) {
    this.authService = authService;
    this.cookieFactory = cookieFactory;
  }

  @Operation(summary = "로그인", description = "bcrypt 검증 후 인증·갱신·위조방지 쿠키 세 개를 발급합니다.")
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
    AuthResult<LoginResponse> result = authService.login(request);
    addAuthCookies(response, result);
    return ResponseEntity.ok(ApiResponse.success(result.body()));
  }

  @Operation(
      summary = "토큰 재발급",
      description = "갱신 쿠키로 세 쿠키를 모두 새 값으로 교체합니다. X-CSRF-TOKEN 헤더가 필요합니다.")
  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
      @CookieValue(value = "refresh_token", required = false) String refreshToken,
      HttpServletResponse response) {
    if (refreshToken == null) {
      throw new AuthException(ErrorCode.AUTH_003_REFRESH_TOKEN_INVALID);
    }
    AuthResult<RefreshResponse> result = authService.refresh(refreshToken);
    addAuthCookies(response, result);
    return ResponseEntity.ok(ApiResponse.success(result.body()));
  }

  @Operation(summary = "로그아웃", description = "갱신 토큰 화이트리스트를 비우고 세 쿠키를 모두 만료시킵니다.")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(Authentication authentication, HttpServletResponse response) {
    Long customerId = Long.valueOf((String) authentication.getPrincipal());
    authService.logout(customerId);
    response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.clearedAccessTokenCookie().toString());
    response.addHeader(
        HttpHeaders.SET_COOKIE, cookieFactory.clearedRefreshTokenCookie().toString());
    response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.clearedCsrfTokenCookie().toString());
    return ResponseEntity.noContent().build();
  }

  private void addAuthCookies(HttpServletResponse response, AuthResult<?> result) {
    response.addHeader(
        HttpHeaders.SET_COOKIE, cookieFactory.accessTokenCookie(result.accessToken()).toString());
    response.addHeader(
        HttpHeaders.SET_COOKIE, cookieFactory.refreshTokenCookie(result.refreshToken()).toString());
    response.addHeader(
        HttpHeaders.SET_COOKIE, cookieFactory.csrfTokenCookie(result.csrfToken()).toString());
  }
}

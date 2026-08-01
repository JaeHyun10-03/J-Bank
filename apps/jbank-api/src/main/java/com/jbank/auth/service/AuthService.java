package com.jbank.auth.service;

import com.jbank.auth.AuthException;
import com.jbank.auth.dto.LoginRequest;
import com.jbank.auth.dto.LoginResponse;
import com.jbank.auth.dto.RefreshResponse;
import com.jbank.auth.jwt.JwtTokenProvider;
import com.jbank.auth.jwt.RefreshTokenStore;
import com.jbank.customer.domain.Customer;
import com.jbank.customer.repository.CustomerRepository;
import com.jbank.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final CustomerRepository customerRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenStore refreshTokenStore;
  private final LoginAttemptService loginAttemptService;

  public AuthService(
      CustomerRepository customerRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider,
      RefreshTokenStore refreshTokenStore,
      LoginAttemptService loginAttemptService) {
    this.customerRepository = customerRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.refreshTokenStore = refreshTokenStore;
    this.loginAttemptService = loginAttemptService;
  }

  @Transactional(readOnly = true)
  public AuthResult<LoginResponse> login(LoginRequest request) {
    if (loginAttemptService.isLocked(request.loginId())) {
      throw new AuthException(ErrorCode.AUTH_002_ACCOUNT_LOCKED);
    }

    Customer customer =
        customerRepository
            .findByLoginId(request.loginId())
            .orElseThrow(() -> new AuthException(ErrorCode.AUTH_001_INVALID_CREDENTIALS));
    if (!passwordEncoder.matches(request.password(), customer.getPasswordHash())) {
      loginAttemptService.recordFailure(request.loginId());
      throw new AuthException(ErrorCode.AUTH_001_INVALID_CREDENTIALS);
    }
    loginAttemptService.reset(request.loginId());

    Long customerId = customer.getCustomerId();
    Tokens tokens = issueTokens(customerId);
    LoginResponse body =
        new LoginResponse(
            String.valueOf(customerId),
            customer.getName(),
            tokens.accessTokenExpiresAt,
            tokens.csrfToken);
    return new AuthResult<>(body, tokens.accessToken, tokens.refreshToken, tokens.csrfToken);
  }

  @Transactional(readOnly = true)
  public AuthResult<RefreshResponse> refresh(String refreshTokenCookieValue) {
    Claims claims =
        jwtTokenProvider
            .parse(refreshTokenCookieValue)
            .orElseThrow(() -> new AuthException(ErrorCode.AUTH_003_REFRESH_TOKEN_INVALID));
    Long customerId = Long.valueOf(claims.getSubject());
    String jti = claims.getId();

    String newJti = refreshTokenStore.rotate(customerId, jti);
    String accessToken = jwtTokenProvider.createAccessToken(customerId);
    String refreshToken = jwtTokenProvider.createRefreshToken(customerId, newJti);
    String csrfToken = UUID.randomUUID().toString();
    OffsetDateTime accessTokenExpiresAt =
        expiresAt(jwtTokenProvider.getAccessTokenTtl().toSeconds());

    RefreshResponse body = new RefreshResponse(accessTokenExpiresAt, csrfToken);
    return new AuthResult<>(body, accessToken, refreshToken, csrfToken);
  }

  public void logout(Long customerId) {
    refreshTokenStore.revokeAll(customerId);
  }

  private Tokens issueTokens(Long customerId) {
    String jti = refreshTokenStore.issue(customerId);
    String accessToken = jwtTokenProvider.createAccessToken(customerId);
    String refreshToken = jwtTokenProvider.createRefreshToken(customerId, jti);
    String csrfToken = UUID.randomUUID().toString();
    OffsetDateTime accessTokenExpiresAt =
        expiresAt(jwtTokenProvider.getAccessTokenTtl().toSeconds());
    return new Tokens(accessToken, refreshToken, csrfToken, accessTokenExpiresAt);
  }

  private static OffsetDateTime expiresAt(long ttlSeconds) {
    return Instant.now().plusSeconds(ttlSeconds).atZone(ZoneId.systemDefault()).toOffsetDateTime();
  }

  private record Tokens(
      String accessToken,
      String refreshToken,
      String csrfToken,
      OffsetDateTime accessTokenExpiresAt) {}
}

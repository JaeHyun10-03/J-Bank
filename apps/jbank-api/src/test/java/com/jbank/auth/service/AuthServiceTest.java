package com.jbank.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.jbank.auth.AuthException;
import com.jbank.auth.dto.LoginRequest;
import com.jbank.auth.jwt.JwtTokenProvider;
import com.jbank.auth.jwt.RefreshTokenStore;
import com.jbank.customer.domain.Customer;
import com.jbank.customer.repository.CustomerRepository;
import com.jbank.global.exception.ErrorCode;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private CustomerRepository customerRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private RefreshTokenStore refreshTokenStore;
  @Mock private LoginAttemptService loginAttemptService;
  @Mock private Customer customer;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService =
        new AuthService(
            customerRepository,
            passwordEncoder,
            jwtTokenProvider,
            refreshTokenStore,
            loginAttemptService);
  }

  @Test
  void 잠긴_계정이면_비밀번호_확인_없이_거절한다() {
    given(loginAttemptService.isLocked("locked-user")).willReturn(true);

    assertThatThrownBy(() -> authService.login(new LoginRequest("locked-user", "pw")))
        .isInstanceOf(AuthException.class)
        .satisfies(
            ex ->
                assertThat(((AuthException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_002_ACCOUNT_LOCKED));
    verify(customerRepository, never()).findByLoginId(any());
  }

  @Test
  void 존재하지_않는_로그인ID면_거절한다() {
    given(loginAttemptService.isLocked("no-such-user")).willReturn(false);
    given(customerRepository.findByLoginId("no-such-user")).willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(new LoginRequest("no-such-user", "pw")))
        .isInstanceOf(AuthException.class)
        .satisfies(
            ex ->
                assertThat(((AuthException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_001_INVALID_CREDENTIALS));
  }

  @Test
  void 비밀번호가_틀리면_실패기록을_남기고_거절한다() {
    given(loginAttemptService.isLocked("user1")).willReturn(false);
    given(customerRepository.findByLoginId("user1")).willReturn(Optional.of(customer));
    given(customer.getPasswordHash()).willReturn("hashed");
    given(passwordEncoder.matches("wrong-pw", "hashed")).willReturn(false);

    assertThatThrownBy(() -> authService.login(new LoginRequest("user1", "wrong-pw")))
        .isInstanceOf(AuthException.class)
        .satisfies(
            ex ->
                assertThat(((AuthException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_001_INVALID_CREDENTIALS));
    verify(loginAttemptService).recordFailure("user1");
  }

  @Test
  void 로그인에_성공하면_실패기록을_초기화하고_토큰을_발급한다() {
    given(loginAttemptService.isLocked("user1")).willReturn(false);
    given(customerRepository.findByLoginId("user1")).willReturn(Optional.of(customer));
    given(customer.getPasswordHash()).willReturn("hashed");
    given(customer.getCustomerId()).willReturn(7L);
    given(customer.getName()).willReturn("정민성");
    given(passwordEncoder.matches("right-pw", "hashed")).willReturn(true);
    given(refreshTokenStore.issue(7L)).willReturn("jti-1");
    given(jwtTokenProvider.createAccessToken(7L)).willReturn("access-token");
    given(jwtTokenProvider.createRefreshToken(7L, "jti-1")).willReturn("refresh-token");
    given(jwtTokenProvider.getAccessTokenTtl()).willReturn(Duration.ofMinutes(15));

    AuthResult<?> result = authService.login(new LoginRequest("user1", "right-pw"));

    verify(loginAttemptService).reset("user1");
    assertThat(result.accessToken()).isEqualTo("access-token");
    assertThat(result.refreshToken()).isEqualTo("refresh-token");
  }

  @Test
  void 갱신토큰이_유효하지_않으면_거절한다() {
    given(jwtTokenProvider.parse("bad-token")).willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.refresh("bad-token"))
        .isInstanceOf(AuthException.class)
        .satisfies(
            ex ->
                assertThat(((AuthException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_003_REFRESH_TOKEN_INVALID));
  }

  @Test
  void 로그아웃하면_갱신토큰_계열을_전부_폐기한다() {
    authService.logout(9L);

    verify(refreshTokenStore).revokeAll(9L);
  }
}

package com.jbank.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jbank.auth.AuthException;
import com.jbank.auth.config.SecurityConfig;
import com.jbank.auth.dto.LoginRequest;
import com.jbank.auth.dto.LoginResponse;
import com.jbank.auth.jwt.AuthCookieFactory;
import com.jbank.auth.jwt.JwtTokenProvider;
import com.jbank.auth.service.AuthResult;
import com.jbank.auth.service.AuthService;
import com.jbank.global.exception.ErrorCode;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class, AuthCookieFactory.class})
@TestPropertySource(
    properties = "jbank.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256")
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AuthService authService;

  @Test
  void 로그인에_성공하면_200과_쿠키_세개를_내려준다() throws Exception {
    given(authService.login(any()))
        .willReturn(
            new AuthResult<>(
                new LoginResponse("1", "정민성", OffsetDateTime.now(), "csrf-abc"),
                "access-token",
                "refresh-token",
                "csrf-abc"));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("user1", "pw"))))
        .andExpect(status().isOk())
        .andExpect(
            result -> {
              String setCookies = String.join(";", result.getResponse().getHeaders("Set-Cookie"));
              org.assertj.core.api.Assertions.assertThat(setCookies).contains("access_token=");
              org.assertj.core.api.Assertions.assertThat(setCookies).contains("refresh_token=");
              org.assertj.core.api.Assertions.assertThat(setCookies).contains("XSRF-TOKEN=");
            });
  }

  @Test
  void 로그인_실패시_401과_에러코드를_반환한다() throws Exception {
    given(authService.login(any()))
        .willThrow(new AuthException(ErrorCode.AUTH_001_INVALID_CREDENTIALS));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("user1", "wrong"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 인증_없이_로그아웃하면_401이다() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "csrf-1"))
                .header("X-CSRF-TOKEN", "csrf-1"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 인증된_사용자가_로그아웃하면_204다() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "csrf-1"))
                .header("X-CSRF-TOKEN", "csrf-1")
                .with(
                    authentication(
                        new UsernamePasswordAuthenticationToken(
                            "9",
                            null,
                            java.util.List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))))
        .andExpect(status().isNoContent());
  }
}

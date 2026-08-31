package com.jbank.internal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jbank.global.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 서비스 간 호출(jbank-product → jbank-api)만 여는 내부 API 전용 필터.
 * {@code /internal/v1/**}는 고객 JWT가 없으므로 SecurityConfig의 PUBLIC_PATHS로 두되,
 * 그 대신 이 필터가 공유 비밀키(X-Internal-Api-Key)를 확인해 인증 없는 호출을 막는다.
 * 다른 경로는 그대로 통과시킨다.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

  private static final String HEADER = "X-Internal-Api-Key";
  private static final String PATH_PREFIX = "/internal/";

  private final String expectedKey;
  private final ObjectMapper objectMapper;

  public InternalApiKeyFilter(
      @Value("${jbank.internal.api-key}") String expectedKey, ObjectMapper objectMapper) {
    this.expectedKey = expectedKey;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!request.getRequestURI().startsWith(PATH_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }

    String providedKey = request.getHeader(HEADER);
    if (providedKey == null || !constantTimeEquals(providedKey, expectedKey)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      objectMapper.writeValue(
          response.getWriter(),
          ApiResponse.error("COMMON_002_UNAUTHORIZED", "내부 API 키가 없거나 올바르지 않습니다"));
      return;
    }
    filterChain.doFilter(request, response);
  }

  // 문자열 길이·내용 비교 시간차로 키를 추측당하지 않도록 타이밍 세이프 비교를 쓴다.
  private static boolean constantTimeEquals(String a, String b) {
    return java.security.MessageDigest.isEqual(
        a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}

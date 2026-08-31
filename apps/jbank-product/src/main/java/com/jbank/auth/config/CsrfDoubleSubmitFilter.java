package com.jbank.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jbank.global.exception.ErrorCode;
import com.jbank.global.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 쿠키·헤더 이중제출 검증(API설계 2.8절). GET류와 로그인은 검증하지 않는다. 이 필터는 DispatcherServlet 이전 단계라
 * GlobalExceptionHandler를 못 타므로, 실패 응답을 직접 같은 ApiResponse 포맷으로 써준다.
 */
public class CsrfDoubleSubmitFilter extends OncePerRequestFilter {

  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
  // 로그인은 문서(API설계 2.8절)가 명시한 예외. 고객 등록은 로그인 이전 익명 사용자가
  // 호출하므로 XSRF-TOKEN 쿠키 자체가 존재할 수 없어 이중제출 검증이 성립하지 않는다.
  private static final Set<String> EXEMPT_PATHS = Set.of("/api/v1/auth/login", "/api/v1/customers");
  // 서비스 간 호출(InternalApiKeyFilter가 인증)은 브라우저 쿠키가 아예 없으니
  // 이중제출 개념 자체가 성립하지 않는다 — jbank-api 쪽 실측 배포에서 발견한
  // 버그와 같은 원인이라 여기도 같이 고친다.
  private static final String EXEMPT_PATH_PREFIX = "/internal/";
  private static final String COOKIE_NAME = "XSRF-TOKEN";
  private static final String HEADER_NAME = "X-CSRF-TOKEN";

  private final ObjectMapper objectMapper;

  public CsrfDoubleSubmitFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (SAFE_METHODS.contains(request.getMethod())
        || EXEMPT_PATHS.contains(request.getRequestURI())
        || request.getRequestURI().startsWith(EXEMPT_PATH_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }

    Optional<String> cookieValue = readCookie(request);
    String headerValue = request.getHeader(HEADER_NAME);
    if (cookieValue.isEmpty() || !cookieValue.get().equals(headerValue)) {
      writeError(response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private Optional<String> readCookie(HttpServletRequest request) {
    if (request.getCookies() == null) {
      return Optional.empty();
    }
    return List.of(request.getCookies()).stream()
        .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
        .map(Cookie::getValue)
        .findFirst();
  }

  private void writeError(HttpServletResponse response) throws IOException {
    ErrorCode errorCode = ErrorCode.COMMON_007_CSRF_TOKEN_INVALID;
    response.setStatus(errorCode.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response
        .getWriter()
        .write(
            objectMapper.writeValueAsString(
                ApiResponse.error(errorCode.name(), errorCode.getMessage())));
  }
}

package com.jbank.auth.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 실측 배포 중 발견한 회귀 — 서비스 간 내부 API(POST/PATCH)가 이중제출 검증에 걸려
 * 전부 403이 났었다(docs/adr/0007 관련 커밋 참고). 내부 API는 브라우저 쿠키가
 * 아예 없으니 검증 대상이 아니어야 한다.
 */
class CsrfDoubleSubmitFilterTest {

  private final CsrfDoubleSubmitFilter filter = new CsrfDoubleSubmitFilter(new ObjectMapper());

  @Test
  void 내부_API_POST_요청은_XSRF_토큰_없이도_통과한다() throws Exception {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    FilterChain chain = Mockito.mock(FilterChain.class);
    given(request.getMethod()).willReturn("POST");
    given(request.getRequestURI()).willReturn("/internal/v1/accounts/withdraw-by-number");
    given(request.getCookies()).willReturn(null);

    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(response, never()).setStatus(Mockito.anyInt());
  }

  @Test
  void 내부_API_PATCH_요청도_XSRF_토큰_없이_통과한다() throws IOException, jakarta.servlet.ServletException {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    FilterChain chain = Mockito.mock(FilterChain.class);
    given(request.getMethod()).willReturn("PATCH");
    given(request.getRequestURI()).willReturn("/internal/v1/contracts/1/mature");
    given(request.getCookies()).willReturn(null);

    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  void 고객용_POST_요청은_XSRF_토큰_없으면_여전히_막힌다() throws Exception {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    FilterChain chain = Mockito.mock(FilterChain.class);
    given(request.getMethod()).willReturn("POST");
    given(request.getRequestURI()).willReturn("/api/v1/accounts");
    given(request.getCookies()).willReturn(null);
    given(response.getWriter()).willReturn(new java.io.PrintWriter(java.io.Writer.nullWriter()));

    filter.doFilterInternal(request, response, chain);

    verify(chain, never()).doFilter(request, response);
    verify(response).setStatus(403);
  }
}

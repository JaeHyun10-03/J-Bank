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

/** 변경 요청의 CSRF 검증과 제거된 내부 경로의 예외 부재를 검증한다. */
class CsrfDoubleSubmitFilterTest {

  private final CsrfDoubleSubmitFilter filter = new CsrfDoubleSubmitFilter(new ObjectMapper());

  @Test
  void 내부_경로도_XSRF_토큰_없이는_거절한다() throws Exception {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    FilterChain chain = Mockito.mock(FilterChain.class);
    given(request.getMethod()).willReturn("POST");
    given(request.getRequestURI()).willReturn("/internal/v1/accounts/withdraw-by-number");
    given(request.getCookies()).willReturn(null);
    given(response.getWriter()).willReturn(new java.io.PrintWriter(java.io.Writer.nullWriter()));

    filter.doFilterInternal(request, response, chain);

    verify(chain, never()).doFilter(request, response);
    verify(response).setStatus(403);
  }

  @Test
  void 내부_경로_PATCH도_XSRF_토큰_없이_거절한다() throws IOException, jakarta.servlet.ServletException {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    FilterChain chain = Mockito.mock(FilterChain.class);
    given(request.getMethod()).willReturn("PATCH");
    given(request.getRequestURI()).willReturn("/internal/v1/contracts/1/mature");
    given(request.getCookies()).willReturn(null);
    given(response.getWriter()).willReturn(new java.io.PrintWriter(java.io.Writer.nullWriter()));

    filter.doFilterInternal(request, response, chain);

    verify(chain, never()).doFilter(request, response);
    verify(response).setStatus(403);
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

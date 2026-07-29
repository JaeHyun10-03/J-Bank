package com.jbank.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestTraceIdFilter extends OncePerRequestFilter {

  public static final String TRACE_ID_HEADER = "X-Request-Id";
  public static final String TRACE_ID_MDC_KEY = "traceId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String traceId = UUID.randomUUID().toString();
    try {
      MDC.put(TRACE_ID_MDC_KEY, traceId);
      response.setHeader(TRACE_ID_HEADER, traceId);
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(TRACE_ID_MDC_KEY);
    }
  }
}

package com.jbank.global.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestTraceIdFilterTest {

  private final RequestTraceIdFilter filter = new RequestTraceIdFilter();

  @Test
  void 요청_처리_중에는_MDC와_응답헤더에_traceId가_채워지고_끝나면_MDC에서_제거된다() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    filter.doFilter(
        request,
        response,
        (req, res) -> assertThat(MDC.get(RequestTraceIdFilter.TRACE_ID_MDC_KEY)).isNotBlank());

    // then
    assertThat(response.getHeader(RequestTraceIdFilter.TRACE_ID_HEADER)).isNotBlank();
    assertThat(MDC.get(RequestTraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
  }
}

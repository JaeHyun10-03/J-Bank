package com.jbank.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

class PrometheusScrapeRequestMatcherTest {

  private static final int SERVER_PORT = 8080;
  private static final int MANAGEMENT_PORT = 9095;

  @Test
  void 별도_관리_서버의_포트로_들어온_prometheus_요청만_일치한다() {
    PrometheusScrapeRequestMatcher matcher = new PrometheusScrapeRequestMatcher(withManagement());

    assertThat(matcher.matches(request(MANAGEMENT_PORT, "/actuator/prometheus"))).isTrue();
  }

  // 관리 포트 미설정·본 포트와 같은 값이면 Boot가 local.management.port를 본 포트 값으로 채운다.
  @Test
  void 관리_포트가_본_포트와_같으면_일치하지_않는다() {
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty("local.server.port", String.valueOf(SERVER_PORT));
    environment.setProperty("local.management.port", String.valueOf(SERVER_PORT));
    PrometheusScrapeRequestMatcher matcher = new PrometheusScrapeRequestMatcher(environment);

    assertThat(matcher.matches(request(SERVER_PORT, "/actuator/prometheus"))).isFalse();
  }

  // 관리 포트가 -1이면 관리 서버가 없어 local.management.port가 없다.
  @Test
  void 관리_서버가_꺼져_있으면_어떤_포트로_와도_일치하지_않는다() {
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty("local.server.port", String.valueOf(SERVER_PORT));
    PrometheusScrapeRequestMatcher matcher = new PrometheusScrapeRequestMatcher(environment);

    assertThat(matcher.matches(request(SERVER_PORT, "/actuator/prometheus"))).isFalse();
    assertThat(matcher.matches(request(MANAGEMENT_PORT, "/actuator/prometheus"))).isFalse();
  }

  @Test
  void 본_포트로_들어온_요청은_Host_헤더가_관리_포트여도_일치하지_않는다() {
    PrometheusScrapeRequestMatcher matcher = new PrometheusScrapeRequestMatcher(withManagement());
    MockHttpServletRequest spoofed = request(SERVER_PORT, "/actuator/prometheus");
    spoofed.setServerPort(MANAGEMENT_PORT);

    assertThat(matcher.matches(spoofed)).isFalse();
  }

  @Test
  void 관리_포트라도_prometheus가_아닌_경로나_GET이_아닌_요청은_일치하지_않는다() {
    PrometheusScrapeRequestMatcher matcher = new PrometheusScrapeRequestMatcher(withManagement());
    MockHttpServletRequest post = request(MANAGEMENT_PORT, "/actuator/prometheus");
    post.setMethod("POST");

    assertThat(matcher.matches(request(MANAGEMENT_PORT, "/actuator/info"))).isFalse();
    assertThat(matcher.matches(request(MANAGEMENT_PORT, "/actuator/prometheus/x"))).isFalse();
    assertThat(matcher.matches(post)).isFalse();
  }

  private static MockEnvironment withManagement() {
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty("local.server.port", String.valueOf(SERVER_PORT));
    environment.setProperty("local.management.port", String.valueOf(MANAGEMENT_PORT));
    return environment;
  }

  private static MockHttpServletRequest request(int localPort, String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.setLocalPort(localPort);
    request.setServerPort(localPort);
    return request;
  }
}

package com.jbank.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 관리 서버가 본 포트와 다른 포트에 실제로 떠 있고, 그 포트로 들어온 GET /actuator/prometheus만 일치한다. 관리 포트는 호스트·Caddy에 노출되지
 * 않으므로 Prometheus가 인증 없이 긁는다(ADR 0013).
 *
 * <p>관리 포트 미설정·본 포트와 같은 값이면 Boot가 local.management.port를 본 포트 값으로 채우고, -1이면 값이 없다. 두 경우 모두 아무 요청도
 * 일치하지 않는다(설정 실수 시 외부 공개 방지).
 */
class PrometheusScrapeRequestMatcher implements RequestMatcher {

  private static final RequestMatcher PROMETHEUS_PATH =
      PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/prometheus");

  private final Environment environment;

  PrometheusScrapeRequestMatcher(Environment environment) {
    this.environment = environment;
  }

  @Override
  public boolean matches(HttpServletRequest request) {
    // 관리 서버가 뜬 뒤에야 생기는 값이라 필터체인 생성 시점이 아니라 요청마다 읽는다.
    Integer managementPort = environment.getProperty("local.management.port", Integer.class);
    Integer serverPort = environment.getProperty("local.server.port", Integer.class);
    // getServerPort()는 Host 헤더 값이라 위조할 수 있다. 실제로 받은 커넥터 포트로 판별한다.
    return managementPort != null
        && !managementPort.equals(serverPort)
        && request.getLocalPort() == managementPort
        && PROMETHEUS_PATH.matches(request);
  }
}

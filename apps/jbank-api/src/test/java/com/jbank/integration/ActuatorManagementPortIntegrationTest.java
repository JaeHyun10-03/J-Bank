package com.jbank.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbank.auth.jwt.JwtTokenProvider;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 운영처럼 관리 포트를 분리한 구성에서 지표·상태 경로의 포트별 접근 규칙을 실제 HTTP로 확인한다(ADR 0013). MockMvc는 관리용 하위 컨텍스트로 라우팅되지 않고
 * 수신 포트도 흉내 내지 못해 쓰지 않는다. RANDOM_PORT가 관리 포트를 무작위 포트로 바꾼다.
 */
// 테스트는 기본으로 지표 내보내기를 꺼서 prometheus 엔드포인트가 없다. 운영과 같게 켠다.
@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {"jbank.transfer.credit-worker.enabled=false", "management.server.port=9095"})
class ActuatorManagementPortIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  static {
    POSTGRES.start();
    REDIS.start();
  }

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("jbank.crypto.pii-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    registry.add("jbank.crypto.hash-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    registry.add("jbank.jwt.secret", () -> "test-secret-key-at-least-32-bytes-long-for-hs256");
  }

  private final HttpClient client = HttpClient.newHttpClient();

  @LocalServerPort private int serverPort;
  @LocalManagementPort private int managementPort;
  @Autowired private JwtTokenProvider jwtTokenProvider;

  @Test
  void 관리_포트의_prometheus는_인증_없이_지표를_반환한다() throws Exception {
    HttpResponse<String> response = get(managementPort, "/actuator/prometheus", null);

    assertThat(managementPort).isNotEqualTo(serverPort);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("jvm_").contains("hikaricp_");
  }

  @Test
  void 관리_포트의_다른_actuator_경로는_인증이_필요하다() throws Exception {
    assertThat(get(managementPort, "/actuator/info", null).statusCode()).isEqualTo(401);
  }

  @Test
  void 본_포트의_prometheus는_미인증_401이고_로그인해도_지표가_없다() throws Exception {
    HttpResponse<String> anonymous = get(serverPort, "/actuator/prometheus", null);
    HttpResponse<String> loggedIn =
        get(serverPort, "/actuator/prometheus", jwtTokenProvider.createAccessToken(1L));

    assertThat(anonymous.statusCode()).isEqualTo(401);
    assertThat(loggedIn.statusCode()).isEqualTo(404);
    assertThat(loggedIn.body()).contains("COMMON_004_NOT_FOUND").doesNotContain("jvm_");
  }

  // 판별이 Host 헤더(getServerPort) 기준이면 위조 요청이 허용되어 401이 아니라 404가 된다.
  @Test
  void 본_포트로_보내고_Host만_관리_포트로_위조해도_401이다() throws Exception {
    String spoofedHost = "localhost:" + managementPort;

    assertThat(rawStatus(serverPort, spoofedHost, "/actuator/prometheus")).isEqualTo(401);
    assertThat(rawStatus(managementPort, spoofedHost, "/actuator/prometheus")).isEqualTo(200);
  }

  @Test
  void 본_포트의_readyz와_livez는_200이고_actuator_health는_없다() throws Exception {
    HttpResponse<String> health = get(serverPort, "/actuator/health", null);

    assertThat(get(serverPort, "/readyz", null).statusCode()).isEqualTo(200);
    assertThat(get(serverPort, "/livez", null).statusCode()).isEqualTo(200);
    assertThat(health.statusCode()).isEqualTo(404);
    assertThat(health.body()).contains("COMMON_004_NOT_FOUND");
  }

  private HttpResponse<String> get(int port, String path, String accessToken) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
    if (accessToken != null) {
      builder.header("Cookie", "access_token=" + accessToken);
    }
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  // JDK HttpClient는 Host 헤더를 바꾸지 못하게 막아 소켓으로 직접 보낸다.
  private int rawStatus(int port, String host, String path) throws Exception {
    try (Socket socket = new Socket("localhost", port)) {
      OutputStream out = socket.getOutputStream();
      String request =
          "GET " + path + " HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n\r\n";
      out.write(request.getBytes(StandardCharsets.US_ASCII));
      out.flush();
      String statusLine =
          new BufferedReader(
                  new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
              .readLine();
      return Integer.parseInt(statusLine.split(" ")[1]);
    }
  }
}

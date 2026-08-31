package com.jbank.batch.interest;

import com.jbank.global.response.ApiResponse;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * product 모듈 분리(W7) 이후, 만기 계약 조회·확정을 jbank-product의 내부 API로 옮긴 클라이언트.
 * 이전에는 {@code ProductContractRepository}로 같은 JVM 안에서 직접 읽고 썼지만, 지금은
 * 두 서비스가 서로 다른 배포 단위라 네트워크 호출로만 접근할 수 있다.
 */
@Component
public class MaturedContractApiClient {

  private final RestClient restClient;

  // 기본값은 이 빈을 실제로 안 쓰는 기존 @SpringBootTest들의 컨텍스트 기동용이다 —
  // InterestMaturityJobIntegrationTest처럼 이 클라이언트를 직접 쓰는 테스트만
  // @MockitoBean으로 대체해서 실제 호출이 없게 한다.
  public MaturedContractApiClient(
      RestClient.Builder restClientBuilder,
      @Value("${jbank.internal.product-service.base-url:http://localhost:8081}") String baseUrl,
      @Value("${jbank.internal.api-key:test-internal-key}") String internalApiKey) {
    // jbank-product도 Tomcat(HTTP/1.1 전용)이라 h2c 협상 자체가 무의미하다 — 아예
    // 1.1로 고정한다(WireMock 기반 테스트에서 h2c RST_STREAM으로 불안정해지는 것도 방지).
    HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    this.restClient =
        restClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("X-Internal-Api-Key", internalApiKey)
            .requestFactory(new JdkClientHttpRequestFactory(httpClient))
            .build();
  }

  public List<MaturedContractDto> findMatured(LocalDate asOf) {
    ApiResponse<List<MaturedContractDto>> response =
        restClient
            .get()
            .uri("/internal/v1/contracts/matured?asOf={asOf}", asOf)
            .retrieve()
            .body(new ParameterizedTypeReference<ApiResponse<List<MaturedContractDto>>>() {});
    return response == null || response.data() == null ? List.of() : response.data();
  }

  // 마킹 실패는 다음 배치 실행에서 재시도해도 안전하다 — 이자 지급 자체는
  // (계약당 한 번만 존재하는) idempotencyKey로 보호되므로, 여기서 예외가 나도
  // 청크 트랜잭션을 롤백시키지 않는다(MaturedContractInterestItemWriter 참고).
  public void markMatured(Long contractId) {
    restClient
        .patch()
        .uri("/internal/v1/contracts/{id}/mature", contractId)
        .retrieve()
        .toBodilessEntity();
  }
}

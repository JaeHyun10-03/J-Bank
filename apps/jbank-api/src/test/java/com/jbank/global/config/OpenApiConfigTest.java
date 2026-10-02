package com.jbank.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

  private final OpenAPI openApi = new OpenApiConfig().jbankOpenApi();

  @Test
  void 버전은_경로_접두사와_같은_v1이다() {
    assertThat(openApi.getInfo().getVersion()).isEqualTo("v1");
  }

  @Test
  void 요청_주소는_명세_문서_기준_상대_경로_하나다() {
    assertThat(openApi.getServers()).extracting(server -> server.getUrl()).containsExactly("/");
  }

  @Test
  void 설명은_현재_쿠키_인증과_CSRF_규칙을_안내한다() {
    assertThat(openApi.getInfo().getDescription())
        .contains("access_token", "refresh_token", "XSRF-TOKEN", "/api/v1/auth/refresh")
        .contains("X-CSRF-TOKEN", "POST", "PATCH", "DELETE", "/api/v1/customers", "csrfToken")
        .contains("COMMON_002_UNAUTHORIZED", "COMMON_007_CSRF_TOKEN_INVALID");
  }

  @Test
  void 설명은_공통_응답_규칙을_유지한다() {
    assertThat(openApi.getInfo().getDescription())
        .contains("\"success\"", "totalElements", "Idempotency-Key", "오프셋");
  }

  @Test
  void Authorize로_X_CSRF_TOKEN_헤더를_입력해_모든_요청에_붙인다() {
    SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get("X-CSRF-TOKEN");

    assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.APIKEY);
    assertThat(scheme.getIn()).isEqualTo(SecurityScheme.In.HEADER);
    assertThat(scheme.getName()).isEqualTo("X-CSRF-TOKEN");
    assertThat(openApi.getSecurity())
        .anySatisfy(item -> assertThat(item).containsKey("X-CSRF-TOKEN"));
  }

  @Test
  void 설명은_Swagger에서_직접_호출하는_순서를_안내한다() {
    assertThat(openApi.getInfo().getDescription()).contains("Authorize", "csrfToken", "새로고침");
  }

  @Test
  void 설명에_인증_도입_전_안내가_남지_않는다() {
    assertThat(openApi.getInfo().getDescription()).doesNotContain("인증은 아직 없습니다", "W3", "쿼리 파라미터로");
    assertThat(openApi.getInfo().getVersion()).doesNotContain("W1", "v0");
  }
}

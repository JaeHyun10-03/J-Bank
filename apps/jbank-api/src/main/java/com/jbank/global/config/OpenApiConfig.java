package com.jbank.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  static final String CSRF_SCHEME = "X-CSRF-TOKEN";

  @Bean
  public OpenAPI jbankOpenApi() {
    // 요청 주소를 명세 문서 기준 상대 경로로 둔다. 운영은 Caddy가 TLS를 끝내 자동 생성 주소가 http가 되고,
    // https Swagger 화면에서 http로 요청하면 브라우저가 막는다.
    return new OpenAPI()
        .servers(List.of(new Server().url("/")))
        // Swagger의 Authorize에 csrfToken을 넣으면 모든 요청에 헤더가 붙는다. 검사 대상이 아닌 요청에는 무해하다.
        .components(
            new Components()
                .addSecuritySchemes(
                    CSRF_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-CSRF-TOKEN")
                        .description("로그인·재발급 응답의 csrfToken")))
        .addSecurityItem(new SecurityRequirement().addList(CSRF_SCHEME))
        .info(
            new Info()
                .title("J-Bank API")
                .version("v1")
                .description(
                    """
                    ## 사용 전에 알아두면 좋은 것

                    **1. 응답은 항상 같은 모양입니다.**
                    ```json
                    { "success": true, "data": { ... }, "error": null }
                    { "success": false, "data": null, "error": { "code": "ACC_001_...", "message": "..." } }
                    ```
                    `success`만 보고 성공/실패를 먼저 판단하고, 실패면 `error.code`로 분기하세요.

                    **2. 인증은 쿠키로 합니다.**
                    `POST /api/v1/auth/login`이 성공하면 서버가 쿠키 세 개를 내려 줍니다.
                    브라우저가 이후 요청에 자동으로 실어 보내므로 `Authorization` 헤더는 쓰지 않습니다.
                    - `access_token`: 인증 쿠키(HttpOnly)
                    - `refresh_token`: `access_token`이 만료되면 `POST /api/v1/auth/refresh`로 재발급(HttpOnly)
                    - `XSRF-TOKEN`: 위조 방지 토큰(스크립트로 읽을 수 있음)

                    인증 쿠키가 없거나 만료되면 401 `COMMON_002_UNAUTHORIZED`입니다.

                    **3. 상태를 바꾸는 요청에는 `X-CSRF-TOKEN` 헤더가 필요합니다.**
                    POST·PATCH·DELETE 요청은 `X-CSRF-TOKEN` 헤더 값이 `XSRF-TOKEN` 쿠키 값과 같아야 합니다.
                    이 값은 로그인·재발급 응답 본문의 `csrfToken`과 같고, 재발급하면 새 값으로 바뀝니다.
                    예외는 로그인과 고객 등록(`POST /api/v1/customers`)입니다.
                    맞지 않으면 403 `COMMON_007_CSRF_TOKEN_INVALID`입니다.

                    **4. 이체·입금·출금은 `Idempotency-Key` 헤더가 필수입니다.**
                    클라이언트가 만든 UUID를 넣습니다. 같은 키로 다시 보내면 처음 처리 결과를 돌려주고 두 번 처리하지 않습니다.

                    **5. 금액은 문자열입니다.**
                    부동소수점 오차를 피하려고 `"10000.00"`처럼 문자열로 내려갑니다.
                    숫자로 바로 파싱하지 말고 표시용 포맷 함수를 하나 두고 거기서만 다루세요.

                    **6. 날짜/시각은 ISO 8601이고 시간대 오프셋이 붙습니다.**
                    예: `2026-10-02T02:21:27Z`, `2026-07-21T09:30:00+09:00`. 오프셋을 보고 변환해서 표시하세요.

                    **7. 목록 조회는 페이지 응답입니다.**
                    ```json
                    { "content": [...], "page": 0, "size": 20, "totalElements": 3, "totalPages": 1 }
                    ```

                    **8. 이 화면에서 직접 호출하려면**
                    1. `POST /api/v1/customers`로 고객을 등록하고(이미 있으면 생략) `POST /api/v1/auth/login`으로 로그인합니다. 브라우저가 쿠키를 저장합니다.
                    2. 로그인 응답의 `data.csrfToken` 값을 오른쪽 위 **Authorize**의 `X-CSRF-TOKEN`에 넣습니다.
                    3. 이후 요청에는 이 헤더가 자동으로 붙습니다.

                    재발급(`/api/v1/auth/refresh`)한 뒤에는 새 `csrfToken`으로 다시 넣고, 페이지를 새로고침해도 다시 넣어야 합니다.
                    """));
  }
}

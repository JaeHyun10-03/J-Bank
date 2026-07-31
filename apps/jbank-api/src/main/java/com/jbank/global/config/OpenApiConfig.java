package com.jbank.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI jbankOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("J-Bank API")
                .version("v0 (W1)")
                .description(
                    """
                    ## 사용 전에 알아두면 좋은 것

                    **1. 응답은 항상 같은 모양입니다.**
                    ```json
                    { "success": true, "data": { ... }, "error": null }
                    { "success": false, "data": null, "error": { "code": "ACC_001_...", "message": "..." } }
                    ```
                    `success`만 보고 성공/실패를 먼저 판단하고, 실패면 `error.code`로 분기하세요.

                    **2. 인증은 아직 없습니다(W3에서 추가 예정).**
                    소유자 확인이 필요한 API(계좌 조회/상태변경/해지)는 임시로
                    `customerId`를 쿼리 파라미터로 직접 받습니다. 로그인 붙기 전까지는
                    프론트에서 이 값을 그대로 넘겨주세요.

                    **3. 금액은 문자열입니다.**
                    부동소수점 오차를 피하려고 `"10000.00"`처럼 문자열로 내려갑니다.
                    숫자로 바로 파싱하지 말고 표시용 포맷 함수를 하나 두고 거기서만 다루세요.

                    **4. 날짜/시각은 ISO 8601입니다.**
                    예: `2026-07-21T09:30:00+09:00`.

                    **5. 목록 조회는 페이지 응답입니다.**
                    ```json
                    { "content": [...], "page": 0, "size": 20, "totalElements": 3, "totalPages": 1 }
                    ```
                    """));
  }
}

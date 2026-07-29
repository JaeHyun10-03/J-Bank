# 2026-07-29 공통 기반 마무리와 Springdoc 노출 문제

목표: W1 공통 기반 나머지 세 항목(금액 Jackson 시리얼라이저, 요청추적ID 필터, Springdoc 노출 확인) 마무리. 겸사겸사 `todo/` 폴더를 깃허브 추적 대상으로 전환.

## 금액 시리얼라이저: `ToStringSerializer` 대신 직접 구현한 이유

**상황.** Jackson에는 이미 `com.fasterxml.jackson.databind.ser.std.ToStringSerializer`가 있어서 `BigDecimal.toString()`을 그대로 쓰면 한 줄로 끝난다.

**문제.** `BigDecimal.toString()`은 스케일이 크거나 지수부가 있으면 `1E+2` 같은 과학적 표기를 낼 수 있다. 금액 필드에 이런 값이 나가면 프론트엔드 파싱이 깨진다.

**해결.** `StdSerializer<BigDecimal>`를 상속한 시리얼라이저를 만들어 `toPlainString()`을 쓰게 했다(`global/config/JacksonConfig.java`). `SimpleModule` 빈으로 등록하면 Spring Boot의 `JacksonAutoConfiguration`이 `Module` 타입 빈을 자동으로 기본 `ObjectMapper`에 붙여준다 — 별도 `ObjectMapper` 커스터마이저를 만들 필요 없음.

**배운 점.** "이미 있는 걸 쓴다"는 원칙도 그 구현체의 실제 동작(엣지케이스)까지 확인한 다음에 적용해야 한다. `toString()`과 `toPlainString()`은 정상 범위 값에서는 같지만 금액처럼 정밀도가 중요한 도메인에서는 이 차이가 버그가 된다.

## Springdoc이 안 떠서 당황한 이유: SecurityConfig가 아예 없었다

**상황.** `application.yml`에 springdoc 경로 설정은 이미 있었다(`/v3/api-docs`, `/swagger-ui.html`). "당연히 되겠지" 하고 확인만 하려 했다.

**증상.** `spring-boot-starter-security`가 의존성에 있는데 `SecurityConfig` 클래스가 프로젝트에 하나도 없었다. 이 상태에서 앱을 띄우면 스프링 시큐리티가 기본 자동설정을 건다:

```
Using generated security password: 325497bb-e75f-4d78-887d-e491c6654a8a
```

이 상태로 확인해보니:

```
GET /v3/api-docs        -> 403
GET /swagger-ui.html    -> 302 (로그인 페이지로)
```

**원인.** 구현계획 문서상 정식 Spring Security 필터체인은 W3 작업이다. W1 시점엔 의도적으로 시큐리티 설정이 없는 상태였고, 그 결과로 기본 정책(전체 인증 요구)이 걸려 있었다.

**해결.** W3 필터체인이 들어오기 전까지 쓸 최소 `SecurityConfig`를 추가했다. `/v3/api-docs/**`, `/swagger-ui/**`만 `permitAll`, 나머지는 그대로 `authenticated()`. 이 클래스는 W3에서 실제 인증/인가 규칙으로 교체될 임시 코드라는 걸 클래스 주석에 남겼다.

**검증.** 로컬 Postgres·Redis(`infra/compose` core 프로파일)를 띄우고 `./gradlew bootRun`으로 실제 기동해서 확인:

```
GET /v3/api-docs           -> 200
GET /swagger-ui/index.html -> 200 (swagger-ui.html에서 302 리다이렉트)
GET /actuator/health       -> 403 (permitAll 안 한 경로는 여전히 막힘, 의도한 대로)
```

`X-Request-Id` 응답 헤더도 같이 확인 — 요청추적ID 필터가 같은 요청에서 정상 동작하는 것도 겸사겸사 검증됐다.

**배운 점.** 설정 파일(`application.yml`)에 값이 있다고 그 기능이 살아있다고 착각하면 안 된다. 의존성 조합(여기선 `spring-security-starter` + 설정 클래스 부재)이 만드는 기본 동작까지 실제로 띄워서 확인해야 한다. "확인" 항목은 정말 실행해서 확인하는 걸로 체크한다.

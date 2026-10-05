# ADR 0013: actuator를 내부 전용 관리 포트로 분리해 Prometheus가 인증 없이 수집

## 상태

승인됨. 2026-10-01.

## 배경

운영 Prometheus(`prometheus.prod.yml`, job `jbank-api`)가 `api:8080/actuator/prometheus`를 긁었지만 api 지표가 하나도 쌓이지 않았다.
2026-10-01 SSM 읽기 전용 조회에서 target health=down, lastError `server returned HTTP status 401`, `up`=0이었다.
필터체인이 `/actuator/health/**`만 공개하고 나머지는 JWT 쿠키 인증을 요구하기 때문이다.

같은 이유로 이미 다른 문제가 있었다. caddy가 `api.j-bank.site`로 8080을 그대로 프록시하고 가입(`/api/v1/customers`)이 공개라,
가입·로그인한 고객 누구나 쿠키로 외부에서 지표를 읽을 수 있었다(perf `metrics_proxy.py`가 바로 이 방식으로 수집한다).

## 결정

- api는 actuator를 관리 포트 9095(`MANAGEMENT_SERVER_PORT`)로 분리한다. 이 포트는 호스트에 매핑하지 않고 caddy도 프록시하지 않아 Compose 내부에서만 닿는다.
- 관리 포트로 들어온 `GET /actuator/prometheus`만 인증 없이 허용한다(`PrometheusScrapeRequestMatcher`). 관리 포트의 다른 actuator 경로와 본 포트 요청은 기존 인증 규칙을 따른다.
- 허용 조건은 설정값이 아니라 실제로 뜬 관리 서버 포트(`local.management.port`)가 본 포트(`local.server.port`)와 다르고, 요청의 실제 수신 포트(`getLocalPort()`)가 그 포트일 때다.
  - 관리 포트를 설정하지 않았거나 본 포트와 같은 값이면 Boot가 `local.management.port`를 본 포트 값으로 채우고, -1이면 값이 없다. 어느 경우든 허용하지 않아 설정 실수로 8080 지표가 외부에 열리지 않는다.
  - `getServerPort()`는 Host 헤더 값이라 본 포트로 `Host: ...:9095`를 보내 위조할 수 있어 쓰지 않는다.
  - 관리 서버가 뜬 뒤에야 값이 생기므로 필터체인을 만들 때가 아니라 요청마다 읽는다.
- 본 포트에는 상태 확인용 `/readyz`·`/livez`를 공개한다. 프론트 서버 상태 확인은 `/readyz`를 부르고, 컨테이너 healthcheck는 관리 포트 readiness를 쓴다.
- 매핑 없는 경로는 404 `COMMON_004_NOT_FOUND`로 응답한다. 전에는 `Exception` 처리기가 500으로 바꿔 "본 포트에 actuator가 없다"를 확인할 수 없었다.
- 쓰이지 않는 메모리 기본 사용자(`UserDetailsServiceAutoConfiguration`)를 제외해 기동 로그의 생성 비밀번호 문구를 없앤다.

## 검토한 대안

- **8080 공개 + caddy에서 `/actuator/*` 차단**: 앱 설정이 바뀌거나 caddy 설정이 빠지면 바로 외부 공개가 된다. 보안 경계가 앱 밖 한 곳에만 있다.
- **Prometheus 전용 인증(Basic·토큰)**: 비밀값 관리와 인증 방식이 하나 더 늘어난다. 내부 전용 포트로 충분하다.
- **perf `metrics_proxy` 재사용**: 고객 계정 로그인에 기대므로 본 포트가 포화되면 수집이 끊기고(ec2-load-test 1차 측정), 로그인 고객의 지표 열람 노출도 그대로 남는다.

## 결과와 한계

- 운영 Prometheus는 `api:9095/actuator/prometheus`를 인증 없이 수집한다. 본 포트에서 actuator가 사라져 로그인 고객의 외부 지표 열람도 막힌다.
- 외부 미인증 `https://api.j-bank.site/actuator/health`는 200에서 404로 바뀐다. 외부 상태 확인은 `/readyz`를 쓴다.
- 옛 compose + 새 이미지이거나 `MANAGEMENT_SERVER_PORT`가 빠지면 actuator가 8080에 남는다. 이때 미인증 지표는 401로 막히지만 로그인 고객이 8080에서 지표를 읽는 기존 노출은 남는다. caddy 차단을 따로 두지 않았으므로 운영 compose의 관리 포트 설정을 지워서는 안 된다.
- Prometheus 설정은 단일 파일 바인드 마운트라 바꾼 뒤 `docker compose restart prometheus`가 필요하다. deploy.sh는 api만 재기동한다.
- perf 대상의 `metrics-proxy`는 새 이미지에서 필요 없어지지만 perf 인프라 변경은 별도 작업으로 남긴다.
- 매핑 없는 경로가 500 대신 404가 되므로 그 경로의 오류 응답 코드가 바뀐다. 프론트는 404/500을 따로 분기하지 않는다.

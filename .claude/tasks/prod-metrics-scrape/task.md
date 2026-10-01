# 작업 명세

## 요구사항 구체화

명세 상태: 확정
원문 요청: 운영 Prometheus(prometheus.prod.yml, job jbank-api, api:8080/actuator/prometheus)가 인증 필요 경로를 긁어 api 지표가 수집되지 않는 것으로 보인다. 먼저 운영에서 실제 401인지 SSM 읽기 전용으로 확인하고,
계획 단계에서 선택지(관리 포트 분리·내부망 허용·Prometheus 인증·metrics_proxy 재사용)를 장단점과 함께 묻는다. Caddy가 api.j-bank.site로 api:8080을 외부 노출하므로 지표 경로가 외부에 열리지 않는 것이 보안 요건이다.
결정 후 구현·테스트·운영 반영 확인(target up, jbank_credit_pending_count 조회)·문서(인프라 문서, ADR 필요 여부)·개발일지까지 진행한다. 기동 로그의 Spring 기본 생성 비밀번호 WARN도 관련 있으면 함께 판단한다.
요청 해석: 운영 Prometheus가 api 지표를 실제로 수집하도록 고치되, 지표 경로가 api.j-bank.site로 외부에 노출되지 않게 한다. 운영 데이터는 바꾸지 않는다.
조사한 사실:
- 2026-10-01 14:33 KST 운영(i-0b71df18394cf8010) SSM 읽기 전용 조회: target `api:8080` health=down, lastError `server returned HTTP status 401`, `up`=0, `jbank_credit_pending_count` 결과 없음. api 컨테이너 안 wget도 401. 외부 `https://api.j-bank.site/actuator/prometheus`는 미인증 401, `/actuator/health`는 200. 단 `/api/v1/customers`(가입)가 공개라 가입·로그인한 아무 고객이나 쿠키로 외부에서 지표를 200으로 읽을 수 있다(perf metrics_proxy가 바로 이 방식). 즉 지금도 로그인 고객에게는 지표가 노출된 상태다(계획 리뷰 1회차 지적, 코드로 확인. 운영 데이터를 바꾸지 않으려고 실제 가입·로그인 재현은 하지 않음).
- SecurityConfig.PUBLIC_PATHS에 `/actuator/health/**`만 있고 prometheus는 없음. 인증 수단은 JWT 쿠키뿐(httpBasic·formLogin 미설정).
- 관리 포트를 분리해도 같은 필터체인이 적용돼 401(ec2-load-test 1차 측정 기록). perf는 MANAGEMENT_SERVER_PORT=9095(호스트 미매핑) + metrics-proxy(perf 전용 고객 계정 로그인) 조합.
- 관리 포트를 분리하면 본 포트(8080)에서는 actuator가 사라진다. 그런데 프론트 상태 경로(apps/frontend/app/api/server-status/route.ts)가 `BACKEND_API_URL/actuator/health`를 외부로 호출한다(응답만 오면 online이라 404여도 online 판정은 유지됨). 컨테이너 healthcheck(docker-compose.prod.yml)와 host/boot.sh의 healthy 대기도 8080 health에 의존.
- 로컬 compose Prometheus(prometheus.yml, host.docker.internal:8080)도 같은 이유로 401일 것.
- Spring Boot 3.5.16. 기본 생성 비밀번호는 UserDetailsServiceAutoConfiguration의 메모리 사용자. httpBasic·formLogin이 없어 그 비밀번호로 인증할 경로는 없지만 로그에 비밀번호 모양 문자열이 남는다.
질문하지 않은 이유: 질문 3개를 했다(아래 표). 관리 포트 값(9095)·설정 위치(운영 compose 환경변수)·Prometheus 재시작 방식은 perf 선례와 배포 경로에서 정해지는 구현 사항이라 묻지 않았다.

원문 요청의 첫 줄은 콜론 뒤에 적고, 길면 이어지는 줄에 계속 적는다. 개인정보·비밀값은 문서에 복사하지 않고 위치와 필요한 의미만 남긴다.

| 질문 ID | 결정 사항과 영향 | 사용자 답변·근거 | 상태 |
| --- | --- | --- | --- |
| Q-01 | 지표 수집 방식: (A) 관리 포트 분리 + 관리 포트의 prometheus만 인증 없이 허용 (B) 8080 공개 + Caddy 차단 (C) Prometheus 전용 인증 (D) metrics_proxy 재사용 | 2026-10-01 사용자: (A) 관리 포트 분리 | 해결 |
| Q-02 | 외부 `/actuator/health`가 사라지는 문제(프론트 server-status) | 2026-10-01 사용자: 본 포트에 `/readyz`·`/livez`를 공개하고 프론트는 `/readyz` 호출 | 해결 |
| Q-03 | 기본 생성 비밀번호 WARN 제거 포함 여부 | 2026-10-01 사용자: 함께 제거(별도 커밋) | 해결 |
| Q-04 | 매핑 없는 경로 응답: 지금은 GlobalExceptionHandler의 `Exception` 처리기가 `NoResourceFoundException`을 500 COMMON_006으로 바꿔, REQ-02·REQ-09의 404 기대가 실제로는 500이 됨(계획 리뷰 2회차 중요-A) | 2026-10-01 사용자: `NoResourceFoundException`을 404 COMMON_004_NOT_FOUND로 처리하는 별도 `fix(api)` 커밋 추가(없는 경로 전체가 500에서 404로 바뀜). 404 기대값 유지 | 해결 |

답변 후 재점검:
- 허용 규칙을 경로만으로 걸면 관리 포트 설정이 빠졌을 때 8080(외부 노출)에서 지표가 공개된다. 그래서 "관리 서버가 본 포트와 다른 포트에 실제로 떠 있고, 요청이 그 실제 바인딩 포트로 들어왔을 때만" 허용한다. 관리 포트 미설정·본 포트와 같은 값·-1이면 아무 요청도 허용하지 않는다(실패 시 닫힘). 기준은 설정값이 아니라 실제 바인딩된 관리 포트다(테스트의 무작위 포트도 같은 규칙으로 동작). 사용자 요건(외부 비노출)에서 바로 도출되는 사항이라 묻지 않음.
  - 실제 바인딩 포트(`local.management.port`)는 관리 서버가 뜬 뒤에야 Environment에 생기고, 보안 필터체인 빈을 만드는 시점에는 아직 없다. 그래서 빈 생성 때 한 번 읽어 고정하지 않고 요청마다 Environment에서 읽는다(계획 리뷰 2회차 권고1).
  - 요청 포트는 `request.getLocalPort()`(실제 수신 커넥터 포트)로 판별한다. `getServerPort()`는 Host 헤더에서 오므로 본 포트로 `Host: ...:9095`를 보내 위조할 수 있다(권고2). 이 위조 사례를 AC-01에 넣는다.
  - 허용 규칙 단위 테스트는 실제 Environment 상태를 흉내 낸다: 관리 포트 미설정·본 포트와 같은 값·-1이면 `local.management.port` 자체가 없는 상태로 둔다(권고3).
- Q-04 반영으로 매핑 없는 경로는 업무 API 포함 전체가 500 COMMON_006 대신 404 COMMON_004로 응답한다. 인증 필터 뒤에서 일어나므로 미인증 요청은 그대로 401이다. apps/frontend의 app·lib·components·middleware에 HTTP 404/500 상태로 분기하는 코드가 없어(grep 확인) 화면 동작 영향 없음.
- 관리 포트는 운영 compose 환경변수 `MANAGEMENT_SERVER_PORT=9095`로 둔다(perf와 같은 방식). 이미지와 compose 반영 순서가 달라도 안전하다: 새 compose+옛 이미지는 지표 401 유지(현재와 같음), 옛 compose+새 이미지는 관리 포트 미설정이라 허용 규칙이 꺼져 401 유지. 로컬·테스트 기본 동작은 그대로(8080에 actuator).
- 로컬 Prometheus(`prometheus.yml`, 호스트 8080)는 지금도 401이고 이번 요청 범위(운영) 밖이라 제외한다. 필요 시 로컬도 `MANAGEMENT_SERVER_PORT`를 주면 같은 방식으로 수집 가능함을 문서에 남긴다.
- 이번 변경으로 본 포트에서 actuator가 사라지므로, 로그인 고객이 외부에서 지표를 읽던 기존 노출도 함께 막힌다(보안 개선). ADR에 기록한다. 단 옛 compose+새 이미지이거나 관리 포트 설정이 빠지면 actuator가 8080에 남아 로그인 고객의 지표 열람 노출도 남는다는 점을 ADR에 함께 적는다(권고7).
- deploy.sh는 api만 재기동한다. Prometheus 설정 파일 변경은 단일 파일 바인드 마운트라 git reset 뒤 컨테이너 재시작이 필요하다. 새 이미지·compose가 실제 api에 반영된 뒤(인스턴스가 꺼진 시간에 병합하면 다음 부팅의 sync-latest 반영 뒤) 1회 `docker compose restart prometheus`를 SSM으로 실행한다(운영 데이터 변경 없음, 수집 몇 초 공백). 재시작 전까지는 지금과 같은 401 상태라 회귀는 아니다. 실행 전 사용자에게 알린다.

## 목표

운영 Prometheus가 api 지표(`up`=1, Hikari·http·`jbank_credit_*`)를 수집하게 한다. 지표 경로는 외부(`api.j-bank.site`)에서 계속 열리지 않는다.

## 범위

- api: 관리 포트로 들어온 `/actuator/prometheus`만 인증 없이 허용, 본 포트 `/readyz`·`/livez` 공개, 기본 생성 사용자 자동 구성 제외, 매핑 없는 경로 404 COMMON_004 처리
- 프론트: server-status가 `/readyz`를 호출
- 운영 compose: api 관리 포트 9095(호스트 미매핑), healthcheck를 관리 포트로, Prometheus 대상 `api:9095`
- 문서: 인프라 아키텍처 2절 다이어그램·3.5절, perf README "운영과 다른 점", ADR 0013, 개발일지
- 운영 반영 확인(SSM 읽기 전용 조회 + Prometheus 1회 재시작)

## 요구사항

| ID | 조건·입력 | 기대 동작·실패 조건 |
| --- | --- | --- |
| REQ-01 | 관리 포트가 별도로 설정된 api에 인증 없이 관리 포트로 `GET /actuator/prometheus` | 200, Prometheus 텍스트 형식 지표(예: `jvm_`·`http_server_requests`·`hikaricp_`). 401/403이면 실패 |
| REQ-02 | 같은 api에 본 포트로 `GET /actuator/prometheus` | 미인증이면 401, 로그인 쿠키가 있으면 404(actuator가 본 포트에 없음). 어느 경우든 지표 본문이 오면 실패 |
| REQ-03 | 관리 포트가 따로 떠 있지 않은 api(미설정·본 포트와 같은 값·-1)에 인증 없이 `GET /actuator/prometheus` | 지금처럼 401. 200이면 실패(설정 누락·실수 시 외부 공개 방지) |
| REQ-04 | 관리 포트 허용 규칙 | 관리 포트로 들어온 요청 중 `/actuator/prometheus`만 허용한다. 관리 포트의 다른 경로(예: `/actuator/info`)나 본 포트 요청은 기존 인증 규칙을 따른다 |
| REQ-05 | 본 포트에 인증 없이 `GET /readyz`, `GET /livez` | 200과 상태 본문. 관리 포트를 분리해도 본 포트에서 응답한다 |
| REQ-06 | api 기동 | `Using generated security password` 로그가 나오지 않고 메모리 기본 사용자(UserDetailsService) 빈이 없다. 기존 로그인·JWT 인증 흐름은 그대로 동작 |
| REQ-07 | 프론트 server-status 호출 | `${BACKEND_API_URL}/readyz`를 호출하고, 응답(200·502·503 등)을 받으면 online, 연결 실패·3초 초과면 offline(기존 판정 유지) |
| REQ-08 | 운영 compose 반영 후 | api 관리 포트 9095는 호스트에 매핑되지 않고 Caddy도 9095로 프록시하지 않는다. 컨테이너 healthcheck는 관리 포트 readiness로 판정. Prometheus 대상은 `api:9095` |
| REQ-09 | 운영 배포 뒤 | Prometheus target health=up, `up{job="jbank-api"}`=1, `jbank_credit_pending_count` 조회 결과 있음. 외부 미인증 `https://api.j-bank.site/actuator/health`는 404(변경 전 200, 본 포트에서 actuator가 사라진 증거), `/actuator/prometheus`는 200 아님, `/readyz`는 200. api 로그에 생성 비밀번호 문구 없음 |
| REQ-10 | 문서 | 인프라 문서·perf README·ADR이 새 수집 경로·보안 경계와 일치한다 |
| REQ-11 | 인증된 요청이 매핑 없는 경로(본 포트 `/actuator/prometheus` 포함)에 도달 | 404와 오류 코드 `COMMON_004_NOT_FOUND`. 500·COMMON_006이면 실패. 미인증이면 지금처럼 401(인증 규칙 우선) |

## 완료 기준

- AC-01(REQ-01·02·04·05): 관리 포트를 분리한 실제 서버 통합 테스트(실제 HTTP 클라이언트, MockMvc 아님)에서 관리 포트 prometheus 200·`jvm_`·`hikaricp_` 지표 포함, 관리 포트 `/actuator/info` 미인증 401, 본 포트 prometheus 미인증 401·로그인 쿠키 404 COMMON_004(지표 본문 없음), 본 포트로 보내면서 `Host` 헤더만 관리 포트(`localhost:<관리 포트>`)로 바꾼 미인증 prometheus 401(포트 위조 불가), 본 포트 `/readyz`·`/livez` 200
- AC-02(REQ-03·05): 관리 포트 미분리 통합 테스트에서 `/actuator/prometheus` 인증 없이 401, `/readyz` 200. 허용 규칙 단위 테스트에서 관리 포트 미설정·본 포트와 같은 값·-1·포트 불일치·경로 불일치는 불허
- AC-03(REQ-06): 통합 테스트에서 UserDetailsService 빈 없음(판정 근거). 기동 로그 문구 검사는 컨텍스트 캐시로 공허해질 수 있어 보조로만 두고 운영 로그로 확인. 기존 로그인 포함 전체 흐름 테스트(FullFlowIntegrationTest) 통과
- AC-04(REQ-07): 프론트 route 테스트가 `/readyz` 호출과 기존 online/offline 판정을 확인
- AC-05(REQ-08): `docker compose -f infra/compose/docker-compose.prod.yml config`로 api에 9095 ports 없음·healthcheck 9095·환경변수 확인, Caddyfile에 9095 없음, prometheus.prod.yml 대상 `api:9095`
- AC-06(REQ-09·SEC-05): 배포 뒤 SSM 읽기 전용 조회(targets·up·jbank_credit_pending_count·api 로그 생성 비밀번호 grep)와 외부 curl(`/actuator/health` 404, `/actuator/prometheus` 비200, `/readyz` 200) 결과를 작업 폴더 logs/prod-check.md에 저장
- AC-07(REQ-10): 문서 갱신, verifier 결과 리뷰에서 일치 확인
- AC-08(REQ-11): GlobalExceptionHandlerTest에서 `NoResourceFoundException`이 404 COMMON_004로 변환됨. AC-01 통합 테스트의 본 포트 로그인 쿠키 prometheus 404 COMMON_004로 실제 요청 경로에서도 확인
- 하네스 verify(백엔드·프론트 전체 검사) 통과

## 하지 않을 일

- perf metrics-proxy 제거·SG 변경(앱 변경으로 프록시 로그인은 불필요해지지만 perf 인프라 변경은 별도 작업). 새 이미지 뒤 설명이 낡는 `infra/compose/perf/docker-compose.target.yml` 6행 주석, `infra/compose/perf/loadgen/prometheus.yml.tpl` 6행 주석, `perf/ec2/metrics_proxy.py` 2~3행 docstring은 후속 작업으로 기록
- 로컬 Prometheus 수집 수정
- Caddy에서 `/actuator/*` 차단 추가(운영 본 포트에는 actuator가 없고, 허용 규칙이 관리 포트에서만 동작해 설정이 빠져도 401이라 중복 방어)
- Grafana 대시보드·알림 추가
- 운영 DB·데이터 변경

## 커밋 계획

1. `feat(api)`: 본 포트에 `/readyz`·`/livez` 공개 (application.yml probes 추가 경로, PUBLIC_PATHS, 테스트)
2. `fix(api)`: 매핑 없는 경로를 500 대신 404 COMMON_004로 응답 (GlobalExceptionHandler, 테스트). Q-04 사용자 결정으로 추가
3. `fix(api)`: 관리 포트로 들어온 `/actuator/prometheus`만 인증 없이 허용 (SecurityConfig, 단위·통합 테스트)
4. `fix(api)`: 기본 생성 비밀번호 사용자 자동 구성 제외 (테스트 포함)
5. `fix(frontend)`: 서버 상태 확인을 `/readyz`로 변경 (route·테스트)
6. `fix(infra)`: 운영 api 관리 포트 9095 분리, healthcheck·Prometheus 대상 변경
7. `docs`: 인프라 문서·perf README·ADR 0013
8. `docs(devlog)`: 개발일지
9. `chore(harness)`: 작업 기록

병합 뒤 후속 PR(운영 반영 확인):
10. `chore(harness)`: 운영 확인 결과(logs/prod-check.md)·완료 기록
11. `docs(devlog)`: 개발일지에 운영 반영 결과 보강

각 커밋은 빌드·기존 테스트가 통과한다. 1~5는 앱 동작만 바꾸고 운영 수집은 6 이후 배포에서 바뀐다. 2는 Q-04 결정에서 나온 커밋이라 기존 승인 범위에 포함된다.

## 적용 영역과 상세 기준

- 프론트: 적용(server-status 경로 변경). 화면 변경 없음.
- 백엔드·데이터: 적용(보안 필터체인·actuator 설정). DB 변경 없음.
- AI: 해당 없음(제품 AI 기능 없음).
- DevOps: 적용(운영 compose·Prometheus 설정·배포 후 확인).
- 보안: 적용(인증 예외 추가·외부 노출 경계·로그의 비밀 모양 문자열).
- 성능: 해당 없음(아래 성능 테스트 참고).
- 전체 흐름: E2E-02(배포 후 실제 연결)·E2E-06(회귀) 적용, 나머지 해당 없음(아래 표). 화면 흐름 변경이 없어 Playwright는 실행하지 않음.

| 기준 ID | 완료 기준·시나리오 | 기대 결과·수치 목표 | 실행 명령 | 시점 | 증거 위치 |
| --- | --- | --- | --- | --- | --- |
| SEC-01 | AC-01·02: 미인증·로그인 요청이 포트·경로별로 허용/거부 | 관리 포트 prometheus만 미인증 200. 본 포트 prometheus 미인증 401·로그인 404, 본 포트+관리 포트 Host 헤더 위조 401, 관리 포트 info 미인증 401, 관리 포트 미분리·같은 포트·-1이면 401 | `apps/jbank-api/gradlew -p apps/jbank-api test spotlessCheck` (verify) | 구현 후 | `.claude/tasks/prod-metrics-scrape/logs`, verify 증거 |
| SEC-02 | 관리 포트 허용이 업무 API로 번지지 않음 | 관리 포트 `/actuator/info` 401, 본 포트 업무 API 기존 인증 유지(기존 컨트롤러 테스트 통과) | 위와 같음 | 구현 후 | 위와 같음 |
| SEC-03 | 해당 없음: 외부 입력 처리 로직 변경 없음 | - | - | - | - |
| SEC-04 | 해당 없음: 쿠키·CSRF·CORS 설정 변경 없음(기존 테스트로 회귀만 확인) | - | - | - | - |
| SEC-05 | AC-03: 기동 로그 비밀번호 모양 문자열 제거, 문서·증거에 비밀값 미기록 | 생성 비밀번호 로그 0건 | verify + 운영 배포 후 `docker logs` grep(읽기 전용) | 구현 후·배포 후 | logs, logs/prod-check.md |
| SEC-06 | 해당 없음: 의존성 변경 없음 | - | - | - | - |
| SEC-07 | AC-05·06: 관리 포트 비노출(호스트 미매핑·Caddy 미프록시·SG 80/443만) | 외부에서 prometheus 200 아님 | `docker compose ... config`, 외부 curl | 구현 후·배포 후 | logs, logs/prod-check.md |
| SEC-08 | 해당 없음: 요청 제한·감사 대상 기능 변경 없음 | - | - | - | - |
| BE-01 | 해당 없음: 업무 규칙 변경 없음 | - | - | - | - |
| BE-02 | AC-01·02·08: actuator·readyz·매핑 없는 경로 응답 코드 계약 | REQ-01~05·REQ-11 표와 일치(없는 경로 404 COMMON_004) | verify(백엔드) | 구현 후 | verify 증거 |
| BE-03 | AC-03: Testcontainers PostgreSQL로 실제 기동 | 통합 테스트 통과 | verify(백엔드) | 구현 후 | verify 증거 |
| BE-04~BE-08 | 해당 없음: 동시성·마이그레이션·외부 연동·비동기·권한 데이터 변경 없음 | - | - | - | - |
| FE-01 | 프론트 lint·tsc·build | 새 오류 0 | verify(프론트) | 구현 후 | verify 증거 |
| FE-03 | AC-04: 응답 코드별·연결 실패·3초 초과 판정 | 기존 판정 유지, 호출 경로 `/readyz` | `npm --prefix apps/frontend test` (verify) | 구현 후 | verify 증거 |
| FE-02·04~07 | 해당 없음: 화면·입력·접근성·성능 변경 없음 | - | - | - | - |
| OPS-03 | AC-05: 운영 compose 설정 렌더링 | 9095 ports 없음, healthcheck 9095, 환경변수 설정 | `docker compose -f infra/compose/docker-compose.prod.yml config` (.env 없이 임시 env로) | 구현 후 | logs/compose-config.txt |
| OPS-04 | AC-05·06: healthcheck가 관리 포트 readiness, 배포 후 api healthy | api `healthy`, readyz 200 | 운영 SSM `docker ps` | 배포 후 | logs/prod-check.md |
| OPS-05 | 배포 순서 안전성(재점검 항목) | 새 compose·옛 이미지, 옛 compose·새 이미지 모두 지표 비공개 | 근거: REQ-03 테스트 + 설정 검토 | 구현 후 | review |
| OPS-07 | AC-06: 운영 Prometheus 수집 | target up, `up`=1, `jbank_credit_pending_count` 결과 있음 | SSM `wget localhost:9090/api/v1/targets`·`query` | 배포 후 | logs/prod-check.md |
| E2E-01 | 해당 없음: 사용자 업무 흐름 변경 없음 | - | - | - | - |
| E2E-02 | 운영 배포 뒤 실제 연결: 외부 `/readyz` 200, 프론트 server-status가 online 응답 | `{"online":true}` | 외부 curl(운영 프론트 `/api/server-status`) | 배포 후 | logs/prod-check.md |
| E2E-03·04·05 | 해당 없음: 중단·재시도·실패 화면·테스트 데이터 격리 로직 변경 없음(server-status 실패 판정은 FE-03 단위 테스트로 확인) | - | - | - | - |
| E2E-06 | 회귀: FullFlowIntegrationTest(가입→로그인→계좌→이체→상품) | 통과 | verify(백엔드) | 구현 후 | verify 증거 |
| OPS-01·02·06·08 | 해당 없음: 빌드·CI·자원·백업 변경 없음(CI는 기존 backend-ci·frontend-ci가 PR에서 실행) | - | - | - | - |

## 일반 테스트 방법

| 완료 기준 | 시나리오·예상 결과 | 테스트 명령·근거 위치 | 실행 시점 |
| --- | --- | --- | --- |
| AC-01 | 관리 포트 분리 RANDOM_PORT 통합 테스트(실제 HTTP): 관리 포트 prometheus 200·`jvm_`·`hikaricp_`, 관리 포트 info 401, 본 포트 prometheus 미인증 401·로그인 쿠키 404 COMMON_004, 본 포트+`Host: localhost:<관리 포트>` 미인증 401, readyz·livez 200 | 새 통합 테스트, verify | 구현 후 |
| AC-08 | GlobalExceptionHandlerTest: `NoResourceFoundException` → 404 COMMON_004. 실제 경로는 AC-01 로그인 쿠키 404로 확인 | 단위·통합 테스트, verify | 구현 후 |
| AC-02 | 관리 포트 미분리 통합 테스트: prometheus 401, readyz 200. 허용 규칙 단위 테스트(미설정·본 포트와 같은 값·-1·포트 불일치·경로 불일치는 불허, 실제 관리 포트+경로 일치만 허용) | 새 통합·단위 테스트, verify | 구현 후 |
| AC-03 | UserDetailsService 빈 없음, FullFlowIntegrationTest 통과. 로그 문구는 운영 로그(AC-06)로 확인 | 통합 테스트(출력 캡처), verify | 구현 후 |
| AC-04 | route.test.ts: fetch URL이 `/readyz`로 끝남, 200·502·503 online, 실패·타임아웃 offline | verify(프론트) | 구현 후 |
| AC-05 | compose config 렌더링 확인 | logs/compose-config.txt | 구현 후 |
| AC-06 | 운영 배포 후 SSM 읽기 전용 조회·외부 curl | logs/prod-check.md | 배포 후(PR 병합 뒤) |

동시 요청: 해당 없음(상태 공유 로직 변경 없음). 배포 후 확인(AC-06)은 PR 병합·backend-cd 배포가 필요해 작업 완료 전 사용자 병합 승인을 받는다.

## 성능 테스트

- 필요 여부와 이유: 불필요. 요청 처리 경로·쿼리 변경 없음. 관리 포트 분리는 별도 커넥터·스레드를 추가하지만 perf 대상에서 이미 같은 구성으로 측정했다(ec2-load-test 이후 모든 회차). 15초 주기 스크랩 1건의 부하는 무시 가능.

## 추가 맥락

- 2026-10-01 운영 확인 원자료 요약은 위 조사한 사실. 배포 후 결과는 logs/prod-check.md.
- 관련: ADR 0010(EC2 단일 인스턴스), perf/README.md "운영과 다른 점", ec2-load-test task Q-07.

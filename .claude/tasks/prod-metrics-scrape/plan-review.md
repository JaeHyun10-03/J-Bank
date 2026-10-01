# 계획 리뷰

## 1회차

회차 계획 지문: 기록 없음(아래 환경 문제로 이 작업 상태에 호출이 기록되지 않음)
회차 판정: 수정 필요

환경 문제: 이 세션은 git worktree(`.claude/worktrees/wizardly-proskuriakova-fd2557`)에서 실행 중인데, `.claude/settings.json`의 hook 명령이 `$CLAUDE_PROJECT_DIR`(메인 체크아웃)의 workflow.py를 부른다. 그래서 verifier 호출이 이 작업(prod-metrics-scrape)이 아니라 메인 체크아웃의 async-credit 상태(done)에 `2026-10-01T06:36:19` plan 호출로 잘못 기록됐다. 이 회차는 공식 계획 검증으로 인정되지 않으며 같은 내용으로 재검증이 필요하다.

### verifier 응답 원문

## 계획 리뷰 1회차: prod-metrics-scrape

**판정: 수정 필요.** 차단 지적은 없습니다. 중요 지적이 3건 있고, 그중 1번은 이대로 테스트를 쓰면 실패하는 기대값 오류입니다.

### 검증 대상
- `.claude/tasks/prod-metrics-scrape/task.md` (명세 상태 "확정" 표기 버전, 커밋 계획 8개, REQ-01~10, AC-01~07)

### 읽은 자료
- 기준 문서: `docs/testing-policy.md`, `docs/testing/{security,devops,backend,frontend,e2e}.md`
- 백엔드 코드
  - `SecurityConfig.java`, `SecurityConfigTest.java`, `FullFlowIntegrationTest.java`(앞부분)
  - `application.yml`, `application-prod.yml`, `application-batch.yml`
  - `@Import(SecurityConfig)`를 쓰는 WebMvcTest 목록
- 인프라
  - `infra/compose/{docker-compose.prod.yml, Caddyfile, deploy.sh, run-batch.sh}`
  - `infra/compose/host/{boot.sh, sync-latest.sh}`, `infra/compose/tests/boot-test.sh`
  - `infra/compose/observability/prometheus.prod.yml`, `infra/compose/perf/docker-compose.target.yml`
  - `.github/scripts/deploy-ec2.sh`, `.github/workflows/backend-cd.yml`(paths)
  - `infra/terraform/modules/ec2/main.tf`(SG)
- 프론트와 perf
  - `apps/frontend/app/api/server-status/route.ts`, `route.test.ts`
  - `perf/ec2/metrics_proxy.py`, `perf/README.md` "운영과 다른 점"
- 문서와 이전 작업 기록
  - `docs/06_J-Bank_인프라아키텍처.md` 2~3.6절, `docs/adr/` 목록(0013은 비어 있는 번호)
  - `.claude/checks.json`
  - ec2-load-test의 Q-07·progress 관련 줄

### 확인하지 못한 범위
- 운영 SSM 조회 결과(2026-10-01 14:33)의 원자료는 task.md 요약만 봤고, 원 출력은 보지 못했습니다.
- Spring Boot 3.5.16 동작은 소스를 실행하지 않고 프레임워크 지식과 perf 선례로 판단했습니다. 해당 항목은 근거를 함께 적었습니다.
- 테스트와 명령은 실행하지 않았습니다(읽기 전용).

### 지적 사항

**[중요-1] REQ-02·AC-01·SEC-01의 "본 포트 prometheus 404" 기대값이 틀렸고, 현재 노출 상태에 대한 사실도 틀렸습니다**

근거:
- `SecurityConfig.java:53`은 `anyRequest().authenticated()`입니다. 본 포트로 들어온 미인증 `/actuator/prometheus`는 DispatcherServlet에 닿기 전에 보안 필터에서 401로 끝납니다. 관리 포트를 분리해도 미인증 요청은 404가 아니라 401입니다.
- 404는 "인증된 요청"일 때만 나옵니다.
- task.md:11의 "외부 /actuator/prometheus 401(현재 외부 노출은 막혀 있음)"도 사실과 다릅니다.
  - `/api/v1/customers`(가입)는 공개 경로입니다.
  - 그래서 가입·로그인한 아무 고객이나 `api.j-bank.site/actuator/prometheus`에서 지금 200을 받습니다.
  - perf `metrics_proxy.py`가 바로 이 방식(일반 고객 계정 쿠키)으로 지표를 가져옵니다.

예상 영향:
- AC-01을 그대로 테스트로 쓰면 실패합니다. 기대값을 401로 바꾸면 "actuator가 본 포트에 없다"는 REQ-02의 핵심을 증명하지 못합니다.
- 이번 변경이 실제로는 기존 노출(로그인 고객의 지표 열람)을 막는 보안 개선이라는 점이 문서·ADR에서 빠집니다.

필요한 수정:
- REQ-02와 AC-01을 둘로 나눕니다.
  - 본 포트 미인증 요청: 401
  - 본 포트에 로그인 쿠키를 붙인 요청: 404 (본문에 지표 없음)
- SEC-01의 기대 결과 문구도 같이 고칩니다.
- 조사한 사실 줄을 정정합니다.
- REQ-09 운영 증거에 외부 미인증 `https://api.j-bank.site/actuator/health`가 404인지(변경 전 200) 확인을 추가합니다.
  - 미인증 prometheus 401은 변경 전에도 같아서 경계 증거가 되지 못합니다.
  - `/actuator/health/**`는 공개 경로라, 404는 "본 포트에서 actuator가 사라졌다"를 운영 데이터 변경 없이 보여 줍니다.

**[중요-2] "실패 시 닫힘"이 관리 포트를 본 포트와 같은 값으로 설정한 경우를 막지 못합니다 (SEC-01, OPS-03, REQ-03)**

근거:
- 재점검 항목(task.md:28)은 "관리 포트가 설정되지 않으면 허용하지 않는다"만 다룹니다.
- `MANAGEMENT_SERVER_PORT`가 8080(본 포트와 같은 값)이 되면 문제가 생깁니다. 이때는 관리 서버가 따로 뜨지 않고 actuator가 8080에 그대로 있습니다.
- 그 상태에서 "요청 포트 == 설정된 관리 포트" 비교를 쓰면 8080의 `/actuator/prometheus`가 인증 없이 열립니다. Caddy가 api:8080을 그대로 프록시하므로 외부에 공개됩니다.

예상 영향:
- 설정 실수 하나로 외부 비노출 요건이 깨집니다. 계획이 이 작업에서 "설정 누락 시 외부 공개 방지"를 명시한 이유와 같은 종류의 위험입니다.

필요한 수정:
- 허용 조건을 "관리 서버가 실제로 다른 포트에 떠 있고(ManagementPortType.DIFFERENT), 요청의 로컬 포트가 그 포트일 때"로 명시합니다.
- 단위 테스트에 같은 포트(=server.port), -1, 미설정 사례를 추가합니다.
- 구현 힌트: 실제 바인딩된 포트(`local.management.port`)를 쓰면 세 경우가 한 번에 정리됩니다. 이 값은 관리 서버가 별도로 떴을 때만 환경에 생깁니다.

**[중요-3] 전체 흐름 영역을 "적용 최소"로 표시했지만 E2E-01~06 ID별 판정이 없습니다 (testing-policy "상세 항목 ID마다 적용 또는 해당 없음과 이유를 기록")**

- 근거: task.md:100은 영역 수준의 문장만 있고, 기준 표에 E2E 행이 없습니다.
- 필요한 수정은 문서만 고치면 됩니다.
  - E2E-06(회귀): FullFlowIntegrationTest로 적용
  - E2E-02(실제 연결): 운영 배포 뒤 `/readyz`와 프론트 server-status 확인으로 대체할지, 해당 없음으로 할지와 그 이유
  - 나머지: 해당 없음과 이유

### 권고 (완료를 막지 않음)

1. **통합 테스트의 관리 포트 값**
   - `@SpringBootTest(RANDOM_PORT)`는 별도 관리 포트가 설정돼 있으면 그 값을 0(무작위)으로 바꿉니다.
   - 설정값(`management.server.port`)과 `getLocalPort()`를 비교하면 테스트에서 REQ-01이 200을 받지 못합니다.
   - 실제 바인딩 포트(`local.management.port`나 `@LocalManagementPort`)를 기준으로 비교하세요.
   - AC-01 테스트는 MockMvc가 아니라 실제 HTTP 클라이언트로 보내야 합니다. MockMvc는 관리용 하위 컨텍스트로 라우팅되지 않고, localPort도 80입니다.
2. **SecurityConfig 생성자에 의존성을 추가할 때**
   - WebMvcTest 7개 이상이 `@Import(SecurityConfig.class)`를 씁니다(AuthControllerTest 등). 그 슬라이스에 없는 빈(예: ManagementServerProperties)을 받으면 이 테스트들이 깨집니다.
   - `Environment`에서 읽는 편이 안전합니다.
   - 배치 프로파일(`web-application-type: none`, compose 환경변수 상속)에서도 컨텍스트가 떠야 합니다. 기존 NONE 통합 테스트가 이를 보장하는지 확인하세요.
3. **AC-03의 로그 문구 검사**
   - Spring 테스트 컨텍스트가 캐시되면, 이 테스트 클래스보다 먼저 컨텍스트가 떠서 출력 캡처에 기동 로그가 안 잡힙니다. 그러면 "문구 없음" 검사가 공허하게 통과할 수 있습니다.
   - 판정의 근거는 UserDetailsService 빈 부재로 두고, 로그 검사는 보조로만 쓰세요.
   - SEC-05의 "운영 배포 후 docker logs grep"을 AC-06 목록에도 넣어 서로 맞추세요.
4. **REQ-01 예시 지표**
   - `http_server_requests`는 요청이 한 번 있은 뒤에야 나타납니다.
   - 테스트에서 먼저 요청을 보내거나, 항상 나오는 `jvm_`·`hikaricp_` 위주로 검사하세요.
5. **Prometheus 재시작 시점**
   - 인스턴스가 꺼진 시간에 병합하면 backend-cd는 배포를 건너뜁니다. 다음 부팅 때 `sync-latest.sh`가 git reset과 api 재기동을 하지만, Prometheus는 이미 이전 inode의 파일로 떠 있어 재시작이 또 필요합니다.
   - 재시작 시점을 "실제 api 반영 뒤"로 적어 두세요.
   - 재시작 전까지는 api:8080 미인증 401로, 지금과 같은 상태라 회귀는 아닙니다.
6. **설명이 낡아지는 파일**
   - 새 이미지가 나가면 `infra/compose/perf/docker-compose.target.yml:6`과 `perf/ec2/metrics_proxy.py:2-3`의 "인증 요구로 401" 설명이 사실과 달라집니다.
   - 후속 작업 기록에 이 두 파일을 명시하세요.
   - `docs/06`의 2절 다이어그램(41행, `api /actuator/prometheus`)도 3.5절과 함께 갱신 대상입니다.
7. **Caddy `/actuator/*` 차단을 제외한 근거**
   - 근거는 타당합니다.
   - 다만 "옛 compose + 새 이미지"이거나 설정이 누락되면, 로그인 고객이 8080에서 지표를 읽는 기존 노출이 남는다는 점을 ADR에 한 줄 남기길 권합니다.

### 질문 4번 기술 확인

(요약 보존) ① 관리 포트에도 앱 필터체인이 적용됨(ServletManagementChildContextConfiguration, perf 실측). ② getLocalPort() 판별 타당(X-Forwarded-Port로 위조 불가, changeLocalPort 기본 false). 남은 문제는 같은 포트 설정(중요-2)과 무작위 포트(권고 1). ③ add-additional-paths로 본 포트 /livez·/readyz 생성, PUBLIC_PATHS 추가 필요, 상세는 기본 never. ④ 배포 순서: 새 compose+옛 이미지, 옛 compose+새 이미지, 배치(web none), 롤백 모두 안전. ⑤ 단일 파일 바인드 마운트 inode 고정이라 restart 필요, up -d는 재생성 안 함.

### 질문 1~3번 확인

- Q-01~03은 사용자 결정과 일치. 9095·설정 위치·재시작 방식·로컬 Prometheus 제외·ADR 작성은 요청 범위와 perf 선례에서 도출되는 구현 사항이라 임의 가정으로 보지 않음. Caddy 차단 제외는 허용 범위(권고 7).
- REQ-01~10 모두 AC에 연결. 연결 내용 오류는 중요-1 하나.
- 적용 제외 판정 타당. 누락은 전체 흐름 ID 판정 하나(중요-3).

### 최종 판정
**수정 필요.** 중요-1~3을 task.md에 반영한 뒤 재리뷰.

### 메인 처리 내역
- 중요-1: 조사한 사실 정정(로그인 고객 노출), REQ-02를 미인증 401·로그인 404로 분리, AC-01·SEC-01 수정, REQ-09·AC-06에 외부 `/actuator/health` 404 추가, 재점검 항목에 보안 개선 기록.
- 중요-2: 허용 조건을 "실제 바인딩된 관리 포트가 본 포트와 다를 때 그 포트"로 변경, REQ-03·AC-02 단위 테스트에 미설정·같은 값·-1 추가.
- 중요-3: E2E-01~06 판정 행 추가(E2E-02는 운영 배포 뒤 실제 연결, E2E-06은 FullFlowIntegrationTest).
- 권고 1·3·4·5·6: AC-01(실제 HTTP·jvm_/hikaricp_), AC-03(빈 부재 판정, 로그는 운영), AC-06(로그 grep), 재시작 시점, 낡는 파일 후속 기록 반영. 권고 2는 구현 시 Environment 사용. 권고 7은 ADR 작성 시 반영.

## 2회차

회차 계획 지문: 기록 없음(hook 경로 문제: 2026-10-01T07:35:06Z 호출이 메인 체크아웃 async-credit 상태에 기록되어 이 작업 상태에 plan 호출이 없음. 공식 회차로 인정되지 않음)
회차 판정: 수정 필요

### verifier 응답 요약

원문은 이전 세션 대화(agentId a9d1a3addb1e6280d)에 있고 이 파일로 옮기지 못했다. 아래는 인계 파일에 남긴 요약이다.

- 판정: 수정 필요. 차단 0, 중요 1, 권고 7.
- 중요-A: REQ-02·REQ-09의 404 기대값이 실제로는 500. GlobalExceptionHandler의 `Exception` 처리기가 `NoResourceFoundException`을 500 COMMON_006으로 바꾼다.
- 권고1: `local.management.port`는 필터체인 빈 생성 시점에 아직 없으므로 요청마다 Environment에서 읽는다.
- 권고2: 포트 판별은 `request.getLocalPort()`. `getServerPort()`는 Host 헤더로 위조 가능. AC-01에 "본 포트 + Host: ...:9095 → 401" 사례 추가.
- 권고3: 단위 테스트는 실제 Environment 상태로(같은 포트·-1이면 `local.management.port` 없음).
- 권고4: 적용 영역의 "E2E-06만 적용"을 E2E-02(배포 후)·E2E-06 적용으로 맞춘다.
- 권고5: 병합 뒤 운영 확인 결과(logs/prod-check.md)·개발일지 보강·완료 기록을 담을 후속 커밋/PR을 커밋 계획에 추가.
- 권고6: 낡는 주석 목록에 `infra/compose/perf/loadgen/prometheus.yml.tpl:6` 추가.
- 권고7: ADR 항목에 "옛 compose+새 이미지 또는 관리 포트 누락 시 로그인 고객의 8080 지표 열람 노출이 남음" 한 줄.
- 그 외: 허용 규칙 설계는 Boot 3.5와 맞음, 커밋 계획 atomic, 기준 ID 판정 누락 없음.

### 메인 처리 내역

- 중요-A: 사용자에게 질문(Q-04). 2026-10-01 사용자 결정으로 `NoResourceFoundException`을 404 COMMON_004로 처리하는 `fix(api)` 커밋을 커밋 계획 2번에 추가, REQ-11·AC-08 신설, BE-02에 연결, AC-01 로그인 쿠키 기대값을 404 COMMON_004로 명시. 매핑 없는 경로 전체 500→404 영향은 재점검 항목에 기록(프론트에 404/500 분기 없음 grep 확인).
- 권고1·2·3: 재점검 항목에 기록, 권고2의 Host 헤더 위조 사례를 AC-01·SEC-01·일반 테스트 방법에 추가.
- 권고4: 적용 영역 문구 수정.
- 권고5: 커밋 계획 10·11번(병합 뒤 후속 PR) 추가.
- 권고6: 하지 않을 일의 낡는 주석 목록에 추가.
- 권고7: 재점검 항목(ADR 기록 내용)에 추가.

## 3회차

회차 기준: 지침상 계획 리뷰 3회차(최초 1회+재리뷰 2회 한도의 마지막). 1·2회차는 hook 경로 문제로 다른 작업 상태에 기록됐고, 이 회차가 이 작업 상태에 기록된 첫 plan 호출(2026-10-01T07:49:31Z)이다.
대상 계획 지문: 062bf0ede2e7bcfefed049ee0d5cc79a039bba1f72dd5446be49b9881e165065
최종 판정: 통과 권고

### verifier 응답 원문

(인용 표시 `> `만 붙이고 내용은 그대로 보존)

> ## 계획 리뷰 3회차 (계획 리뷰 한도의 마지막 회차): prod-metrics-scrape
>
> 차단·중요 지적은 없습니다. 2회차의 중요-A와 권고 1~7은 모두 task.md에 반영됐습니다. 아래 권고 5건은 구현할 때 반영하면 되고, 완료를 막지 않습니다.
>
> ### 검증 대상
> - worktree `/Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/worktrees/wizardly-proskuriakova-fd2557`의 `.claude/tasks/prod-metrics-scrape/task.md` 현재본
>   - 범위: Q-01~04, REQ-01~11, AC-01~08, 커밋 계획 1~9와 병합 뒤 후속 10·11
> - 같은 폴더의 `plan-review.md` (1회차 원문, 2회차 요약과 처리 내역)
>
> ### 읽은 자료
> - 기준 문서
>   - `docs/testing-policy.md`, `docs/testing/backend.md`
>   - `docs/testing/*.md`에서 ID 목록만 확인 (SEC-01~08, BE-01~08, FE-01~07, OPS-01~08, E2E-01~06)
> - 백엔드 코드와 테스트
>   - `GlobalExceptionHandler.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest.java`
>   - `SecurityConfig.java`, `SecurityConfigTest.java`, `CsrfDoubleSubmitFilter.java`, `JwtAuthenticationFilter.java`, `WebMvcConfig.java`
>   - `application*.yml`
>   - 테스트 전체 grep: 500·COMMON_006·actuator·404 기대값
> - 프론트: `app/api/server-status/route.ts`, `lib/server-status.ts`, 404·500 분기 grep
> - 인프라와 perf
>   - `infra/compose/docker-compose.prod.yml`
>   - 저장소 전체 `actuator|readyz|livez|MANAGEMENT_SERVER_PORT` grep (scripts/up.sh, perf/run-10m.sh 포함)
> - 문서: `docs/03_J-Bank_API설계.md`의 COMMON_004·006 정의, `docs/adr/` 목록 (0013은 비어 있음)
> - 이전 작업 커밋 계획 관례: async-credit 등
>
> ### 확인하지 못한 범위
> - 테스트와 명령은 실행하지 않았습니다 (읽기 전용). Spring Boot 3.5와 Security 6.5 동작은 소스 실행 없이 프레임워크 지식으로 판단했습니다.
> - 관리 포트 하위 컨텍스트에서 매핑 없는 경로가 어떤 예외를 던지는지(아래 권고 4)는 실행으로 확인하지 못했습니다.
> - 2회차 verifier 원문은 남아 있지 않아 요약만 대조했습니다.
>
> ### 이전 지적 처리 확인
>
> **중요-A: 해결.**
> - Q-04 (task.md:26)는 사용자 결정으로 기록돼 있습니다.
> - 반영 위치
>   - 커밋 계획 2번 `fix(api)` (task.md:90)
>   - REQ-11 (:65), AC-08 (:76), BE-02 연결 (:126)
>   - AC-01 로그인 쿠키 404 COMMON_004 (:69)
> - 근거 코드와 대조한 결과
>   - `GlobalExceptionHandler.java:40-45`의 `Exception` 처리기가 지금은 500 COMMON_006을 반환합니다. 따라서 수정이 꼭 필요합니다.
>   - `ErrorCode.java:9`에 COMMON_004_NOT_FOUND(404)가 이미 있습니다.
>   - `docs/03_J-Bank_API설계.md:118`의 "요청한 자원이 존재하지 않음" 정의와 맞으므로 API 설계 문서는 고칠 필요가 없습니다.
> - 정적 리소스 기본 매핑을 끄는 설정(add-mappings)이 application*.yml에 없습니다. 그래서 본 포트에서는 실제로 `NoResourceFoundException`이 납니다. 처리 대상을 이 예외로 잡은 것이 맞습니다.
>
> **권고 1~7: 모두 해결.**
>
> | 권고 | 반영 위치 |
> | --- | --- |
> | 1. 관리 포트를 요청마다 읽기 | 재점검 :30 |
> | 2. Host 헤더 위조 사례 | 재점검 :31, AC-01 :69, SEC-01 :117, 일반 테스트 :146 |
> | 3. 단위 테스트의 Environment 상태 | 재점검 :32 |
> | 4. 적용 영역 E2E 문구 | :113 |
> | 5. 병합 뒤 후속 커밋 | 커밋 10·11 :99-101 |
> | 6. 낡는 주석 목록 | `prometheus.yml.tpl` 6행 추가 :81 |
> | 7. ADR에 남는 노출 기록 | 재점검 :36 |
>
> ### 이번 회차 중점 확인 결과
>
> **1. 매핑 없는 경로가 500에서 404로 바뀌는 영향**
> - **기존 테스트**: `src/test` 전체에 500·COMMON_006·INTERNAL_SERVER_ERROR·actuator를 기대하는 테스트가 없습니다. 깨질 기존 테스트도 없습니다.
> - **보안 필터 우선순위**
>   - `SecurityConfig.java:53`의 `anyRequest().authenticated()` 때문에 공개 경로가 아닌 곳은 미인증 요청이 DispatcherServlet에 닿기 전에 401로 끝납니다.
>   - 로그인 쿠키를 붙인 GET은 CSRF 검사를 받지 않습니다 (`CsrfDoubleSubmitFilter.java:24,41`). 그래서 AC-01의 로그인 쿠키 404 COMMON_004 기대값은 실제로 나올 수 있는 값입니다.
> - **REQ-09와 맞는지**
>   - `/actuator/health/**` 패턴은 끝의 `/**`가 0개 구간과도 맞으므로 `/actuator/health`에도 적용됩니다.
>   - 지금 외부 미인증 `/actuator/health`가 200인 사실(task.md:11)도 이를 뒷받침합니다.
>   - 변경 뒤에는 본 포트에서 permitAll을 지나 핸들러가 없으므로 Q-04 처리기로 404가 됩니다. REQ-09의 기대와 맞습니다.
> - **프론트**
>   - `lib/server-status.ts:26-28`은 502·503·504만 분기합니다.
>   - `route.ts`는 응답이 오기만 하면 online입니다.
>   - 404·500 분기 코드가 없다는 계획의 설명이 맞습니다.
> - **그 밖의 actuator 호출처**
>   - `scripts/up.sh:41`, `perf/run-10m.sh:36`, 로컬 `docker-compose.yml:52`는 모두 관리 포트가 없는 로컬 8080을 대상으로 합니다. 영향이 없습니다.
>   - perf 대상은 이미 9095를 씁니다.
>
> **2. 질문 누락과 임의 가정**
> - 새로 생긴 제품 결정 공백은 없습니다.
> - Q-04로 커밋 2번을 추가한 것은 사용자 답변("별도 fix(api) 커밋 추가")에 승인 범위까지 포함돼 있어 임의 가정이 아닙니다.
>
> **3. REQ·AC·기준 ID 연결**
> - REQ-01~11은 모두 AC-01~08 중 하나 이상에 연결돼 있습니다.
> - 다섯 영역의 상세 ID는 모두 "적용" 또는 "해당 없음과 이유"로 판정돼 있습니다.
>
> **4. 커밋 계획**
> - 순서가 맞습니다. 2(404 처리)가 3(AC-01 통합 테스트의 404 COMMON_004 기대)보다 앞섭니다.
> - 각 커밋은 다른 커밋 없이도 빌드되고 테스트가 통과할 수 있습니다.
> - 테스트를 해당 수정 커밋에 함께 넣는 방식은 이전 작업(async-credit 커밋 7 등)과 같은 관례이고, 1·2회차에서도 문제 삼지 않았습니다.
>
> **5. 배포 순서**
> - 새 compose와 옛 이미지 조합: 관리 포트 9095가 표준 속성이라 옛 이미지에서도 9095 readiness가 동작합니다. 지표는 401입니다.
> - 옛 compose와 새 이미지 조합: 8080 readiness와 401이 그대로입니다.
> - `docker-compose.prod.yml:42` 대조 결과, OPS-05의 판단이 타당합니다.
>
> ### 지적 사항
>
> 차단: 없음
> 중요: 없음
>
> **[권고 1] REQ-11의 "미인증이면 401" 문구가 REQ-09와 겉보기에 충돌합니다**
> - 근거: task.md:65는 "미인증이면 401"이라고 씁니다. 그런데 REQ-09(:63)는 미인증 `/actuator/health`가 404라고 기대합니다. 이 경로는 공개 경로 패턴 안에 있어서 404가 맞습니다.
> - 영향: 이 문구 그대로 테스트를 쓰면 기대값이 헷갈릴 수 있습니다. 동작에는 문제가 없습니다.
> - 수정: "공개 경로가 아닌 경로에 미인증이면 401, 공개 경로 패턴 안의 없는 경로(예: `/actuator/health`)는 미인증이어도 404"로 바꿉니다.
>
> **[권고 2] REQ-09의 경계 증거를 병합 전에도 확인하기**
> - 근거: 외부 `/actuator/health` 404는 AC-06(배포 뒤)에서만 확인합니다.
> - 영향: 병합 전에 잡을 수 있는 결함을 운영 배포 뒤에야 발견합니다. 예를 들어 다른 예외가 나서 500이 되는 경우입니다.
> - 수정: AC-01 통합 테스트에 "관리 포트를 분리했을 때 본 포트 미인증 `/actuator/health`가 404 COMMON_004"를 한 줄 추가합니다.
>
> **[권고 3] Host 위조 사례가 공허하게 통과하지 않도록 하기**
> - 근거: AC-01의 "본 포트 + `Host: localhost:<관리 포트>` → 401"은 위조가 없어도 401이 나옵니다. JDK HttpURLConnection과 java.net.http.HttpClient는 기본값으로 Host 헤더를 직접 바꾸지 못하게 막습니다.
> - 영향: 클라이언트가 Host 헤더를 조용히 무시해도 테스트가 통과합니다. 그러면 `getServerPort()` 회귀를 잡지 못합니다.
> - 수정: Host 헤더를 실제로 보내는 클라이언트를 씁니다. 예: Apache HttpClient 5 기반 RestTemplate·RestClient, 또는 소켓 직접 전송.
>   - 가능하면 같은 Host 헤더로 관리 포트에 보낸 요청은 200인 대조 사례도 함께 둡니다.
>
> **[권고 4] 관리 포트의 매핑 없는 경로는 여전히 500일 가능성 (실행 미확인)**
> - 근거: 관리 포트 하위 컨텍스트에는 정적 리소스 처리기가 없습니다. 그래서 `NoResourceFoundException`이 아니라 `NoHandlerFoundException`이 날 수 있습니다. 상위 컨텍스트의 `@RestControllerAdvice`가 적용되면 `Exception` 처리기로 500 COMMON_006이 됩니다.
> - 영향: 외부에서는 이 포트에 닿을 수 없으므로 보안·제품 영향은 없습니다. 다만 REQ-11은 범위를 본 포트로 한정하지 않았습니다.
> - 수정: 둘 중 하나를 고릅니다.
>   - REQ-11의 범위를 "본 포트"로 명시합니다.
>   - 커밋 2에서 `NoHandlerFoundException`도 같은 404로 처리합니다.
>
> **[권고 5] 리뷰 기록 보존**
> - 근거
>   - plan-review.md:149에 따르면 2회차 verifier 원문이 보존되지 않았습니다.
>   - `docs/testing-policy.md:78`은 "실제 반환 내용을 plan-review.md에 보존"하라고 요구합니다.
>   - 1·2회차는 hook 경로 문제로 이 작업의 상태에 기록되지 않았습니다.
> - 영향: 기록을 추적하기 어렵습니다. 계획 내용에는 영향이 없습니다.
> - 수정: 이번 3회차 응답은 원문 그대로 plan-review.md에 보존합니다. 회차를 몇 번으로 세었는지(지침 기준 3회차)도 명시합니다.
>
> ### 적용 제외 항목의 타당성
> - 다음 제외는 모두 타당합니다.
>   - SEC-03·04·06·08, BE-01·04~08
>   - FE-02·04~07, OPS-01·02·06·08
>   - E2E-01·03~05, 성능
> - BE-08 "권한"은 해당 없음으로 표시했습니다. 인증 예외의 회귀는 SEC-01·02가 맡으므로 빠진 부분은 없습니다.
>
> 최종 판정: 통과 권고

### 메인 처리 내역

task.md를 바꾸면 계획 지문이 바뀌어 한도 밖 재리뷰가 필요하므로, 권고는 현재 명세 범위 안에서 구현 시 반영한다.
- 권고1: REQ-11 "미인증 401"은 공개 경로가 아닌 경로에 해당. 공개 패턴 안 없는 경로(`/actuator/health`)는 REQ-09대로 404. 테스트 기대값을 이 구분대로 작성.
- 권고2: AC-01 통합 테스트에 본 포트 미인증 `/actuator/health` 404 COMMON_004 단언을 추가(REQ-09 경계를 병합 전에 확인).
- 권고3: Host 위조 사례는 Host 헤더를 실제로 보내는 방식(소켓 직접 전송 등)으로 작성하고, 같은 Host 헤더로 관리 포트에 보낸 요청 200 대조 사례를 함께 둔다.
- 권고4: REQ-11 문구(매핑 없는 경로 404)를 그대로 지키도록 커밋 2에서 `NoHandlerFoundException`도 404 COMMON_004로 처리.
- 권고5: 이 회차 원문 보존, 회차 기준 명시.

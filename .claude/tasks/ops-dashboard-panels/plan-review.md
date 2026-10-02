# 계획 리뷰

## 1회차

회차 계획 지문: 1e06ccf36f420867853fe67db48cf7307a20b43380597078307deb416cb41396 (2026-10-02T00:33:51Z 호출, verifier agentId afff9bd54107a4953)
회차 판정: 수정 필요

### verifier 응답 원문

(인용 표시 `> `만 붙이고 내용 그대로 보존)

> ## 계획 리뷰 1회차: ops-dashboard-panels
>
> **최종 판정: 수정 필요**
>
> 이유는 중요-1 하나입니다. 운영에 실제로 트래픽이 있는데도 5xx 비율 패널이 빈 그래프로 나올 수 있습니다. 그런데 REQ-05는 이 경우의 동작을 정하지 않았고, REQ-07은 이를 판정할 기준이 없습니다. 차단 지적은 없고 나머지는 권고입니다.
>
> - 검증 대상: `.claude/tasks/ops-dashboard-panels/task.md`(명세 상태: 확정, worktree 기준)
> - 적용 기준: OPS-01, OPS-03, OPS-07. 해당 없음으로 표기된 항목은 OPS-02·04·05·06·08, SEC, BE, FE, E2E, 성능입니다.
>
> ### 읽은 자료
> - 기준: `docs/testing-policy.md`, `docs/testing/devops.md`
> - 대시보드·Grafana: `infra/compose/observability/provisioning/dashboards/json/jbank-transfer.json`, `dashboards.yml`, `datasources.yml`, perf 대시보드 `infra/compose/perf/loadgen/provisioning/dashboards/json/jbank-perf.json`(패널 5·9·10·20~22)
> - Prometheus·배포: `prometheus.prod.yml`, `prometheus.yml`, `infra/compose/docker-compose.prod.yml`, `infra/compose/docker-compose.yml`(grafana 마운트), `infra/compose/deploy.sh`, `.github/workflows/backend-cd.yml`, `.github/scripts/deploy-ec2.sh`
> - 앱 코드: `ProductController.java`, `ProductService.java`, `ProductRepository.java`, `SecurityConfig.java`, 필터 3종, `AuditLogListener.java`, `PendingCreditWorker.java`, `CreditApplier.java`, `application.yml`
> - 문서·이전 작업: `docs/06_J-Bank_인프라아키텍처.md` 3.5절, `.claude/tasks/prod-metrics-scrape/logs/prod-check.md`
> - 그 밖에 저장소 전체에서 `이체 성능|jbank-transfer`와 `readyz|livez`를 검색했습니다.
>
> ### 확인하지 못한 범위
> - task.md 12행에 적힌 운영 Prometheus 조회 결과(계열 목록, uri·status 라벨)가 실제와 같은지는 직접 확인하지 못했습니다. 읽기만으로는 확인할 수 없어 그대로 사실로 두고 검토했습니다.
> - 관리 포트 요청이 `http_server_requests`에 잡히지 않는다는 점도 코드로는 확인하지 못했습니다. 운영에서 관찰한 결과로만 받아들였습니다.
> - Grafana 11.3.1이 실제로 대시보드를 로드하는지는 구현 후에 검증할 대상입니다.
>
> ### 지적 사항
>
> **중요-1: 요청은 있는데 5xx가 없을 때의 동작이 정해지지 않았고, REQ-07 기대 결과는 오류율 쿼리에 맞지 않습니다.** 기준: REQ-05, REQ-07, OPS-07
> - 근거
>   - task.md:12에 따르면 운영의 status 라벨은 200·401·404뿐이라 5xx 계열 자체가 없습니다.
>   - 단순 나눗셈(`sum(rate(..status=~"5.."))/sum(rate(..))`)은 분자가 빈 벡터라 결과도 비어 있습니다. 그래서 트래픽이 있어도 5xx 패널은 계속 "No data"로 나옵니다.
>   - 그러면 task.md:27이 구분하려던 "요청 없음"과 "오류 0"이 화면에서 구분되지 않습니다.
>   - perf 원본 패널 5(jbank-perf.json:229)는 `(5xx or 0 * sum(all)) / sum(all)` 패턴으로 이 문제를 처리합니다. 그런데 REQ-05(task.md:49)에는 "0으로 채우지 않음"만 적혀 있어, 구현 때 이 `or 0 *` 부분을 빼라는 뜻으로 읽힐 수 있습니다.
>   - REQ-07(task.md:51)의 기대 결과 "해당 uri 계열이 나오고"는 전체 합계 쿼리(uri 라벨 없음)에는 해당되지 않습니다. 그래서 오류율 쿼리의 통과 여부를 판정할 수 없습니다.
> - 영향: 운영자가 5xx가 0인지, 집계가 안 되는 것인지 구분하지 못합니다. 결과 리뷰에서는 REQ-07 판정이 임의 해석에 맡겨집니다.
> - 필요한 수정
>   - REQ-05에 다음을 명시합니다.
>     - 요청이 있고 해당 오류가 없으면 0
>     - 요청이 없으면 값 없음
>     - 위 패턴을 쓰되 `by` 없이 전체 합계로 계산
>   - REQ-07과 AC-02의 기대 결과를 쿼리별로 나눕니다.
>     - p95: `method="GET",uri="/api/v1/products"` 계열이 숫자값으로 나오고, `/readyz`·`/livez`·`/actuator…` 라벨은 없음
>     - 5xx: 단일 계열, 값 0
>     - 4xx: 단일 계열, 실제 값 기록
>
> **권고-1: REQ-07·REQ-02의 실행 조건과 NaN 판정 기준을 정해야 합니다.**
> - `$__rate_interval`은 Prometheus API에서 그대로 쓸 수 없습니다. 대신 쓸 고정 창(예: `[5m]`)을 명시해야 합니다.
> - 새로 생긴 카운터 계열은 첫 스크랩 전에 들어온 요청만 있으면 rate가 0입니다. 이 경우 p95와 비율이 모두 NaN이 됩니다.
>   - 호출을 15초 스크랩 간격보다 넓게 나눠서 보내야 합니다(예: 5초 간격으로 1~2분).
>   - NaN이 나오면 실패로 본다는 기준이 필요합니다.
> - REQ-02의 반영 지연 p95는 운영에 이체가 없으면 NaN입니다(`CreditApplier.java:46-49`의 히스토그램이 rate 0). "결과가 있어야 함"이 계열만 있으면 되는지, NaN도 허용하는지 명시해야 합니다.
> - REQ-02(task.md:46)는 "세 쿼리"라고 적었지만 실제 식은 건수·나이·p95·max의 4개입니다.
>
> **권고-2: GC 최대 멈춤의 단위를 검증할 수 있게 해야 합니다.** 기준: REQ-04, AC-01
> - perf 원본 패널 10(jbank-perf.json:461-489)은 `percentunit` 패널에 `max(jvm_gc_pause_seconds_max)`를 함께 넣었습니다. 그래서 초 단위 값이 %로 표시됩니다(0.05초가 5%로 보임).
> - 그대로 옮기면 REQ-04의 "(초)"를 어깁니다. 그런데 AC-01 구조 검사에는 단위 확인이 없습니다.
> - 새 계열마다 단위(override 또는 패널 분리)를 AC-01 검사 항목에 넣어야 합니다.
> - 기존 5개 패널이 그대로인지 비교할 기준점(예: `git show <시작 커밋>:파일`)도 함께 적으면 좋습니다.
>
> **권고-3: p95 패널에 `uri="/**"`(404)·`UNKNOWN`(401) 계열을 남길지 정하지 않았습니다.** 기준: REQ-03
> - 인터넷에서 들어오는 탐색 요청이 "API별" 목록을 채웁니다.
> - 표시 범위에 대한 선택이라 차단 사유는 아닙니다. 다만 task.md:26처럼 결정과 이유를 한 줄 남기면 결과 리뷰에서 판정할 수 있습니다.
>
> **권고-4: OPS-07 행에 알림 제외와 5xx 미검증 한계를 적어야 합니다.**
> - devops.md:19의 OPS-07은 의도한 오류를 만들고 알림이 도착하는지까지 확인하는 기준입니다.
> - 알림은 사용자 결정으로 보류했다(task.md:65)는 점을 OPS-07 행에도 적어야 합니다.
> - 없는 경로로 GET을 보내 404(uri `/**`)를 만들면 4xx 계열이 오르는지 확인할 수 있습니다.
> - 5xx는 운영에서 안전하게 만들 수 없으므로, 검증하지 못한 한계로 기록해야 합니다.
>
> **권고-5: 병합하면 운영 api가 재기동됩니다.**
> - backend-cd.yml:8의 `infra/compose/**` 경로 때문에 배포가 돌고, deploy.sh:13-14가 api를 다시 만듭니다.
> - 단일 인스턴스라 몇 초 동안 끊깁니다. task.md:15·81에 사실로는 적혀 있습니다.
> - 기존 배포 경로라 차단 사유는 아니지만, 병합 시점을 사용자와 맞추라고 적어 두면 좋습니다.
>
> ### 확인 요청 항목별 결과
> 1. **질문 누락·임의 가정**
>    - Q-01과 Q-02는 원문 요청과 답변에 맞게 반영됐습니다.
>    - 상태 확인 경로를 사용자에게 묻지 않고 제외한 판단은 타당합니다.
>      - 컨테이너 healthcheck는 관리 포트를 쓰고(docker-compose.prod.yml:45), `/readyz`는 프론트 server-status가 부르는 경로(route.ts:14)라 업무 API 트래픽이 아닙니다.
>      - 단점은 DB 장애로 `/readyz`가 503을 내도 5xx 비율에 잡히지 않는다는 것입니다. 이는 Hikari 패널로 보완됩니다.
>    - 새로 빠진 제품 선택은 중요-1(오류 없음일 때 0 표시)과 권고-3입니다.
> 2. **REQ·AC·기준 ID 연결**
>    - REQ-01~09는 모두 AC-01~04 중 하나에 연결돼 있습니다.
>    - 판정 기준이 불명확한 곳은 중요-1(REQ-05·07)과 권고-1·2입니다.
> 3. **REQ-07 검증 방법**
>    - `GET /api/v1/products`는 인증 없이 공개됩니다(SecurityConfig.java:39).
>    - 호출 경로는 `ProductController.list`(:35-38) → `ProductService.list`의 `@Transactional(readOnly = true)` `findByStatus`(:46-49)이고 쓰기가 없습니다.
>    - 필터 3종(RequestTraceId·Jwt·CsrfDoubleSubmit)과 AuditLogListener(이체·상태·등급 이벤트만 처리)도 이 요청에서 쓰기를 하지 않습니다.
>    - 따라서 운영 데이터는 바뀌지 않습니다.
>    - `http.server.requests` 히스토그램이 켜져 있어(application.yml:65-66) p95를 계산할 수 있고, uri 라벨은 `/api/v1/products`가 됩니다.
>    - 방법은 적절합니다. 다만 호출 간격과 NaN 판정은 권고-1을 반영해야 합니다.
> 4. **Grafana 반영 경로와 backend-cd 경로: 모두 사실과 일치합니다.**
>    - Grafana 프로비저닝은 디렉터리 단위 읽기 전용 마운트입니다(docker-compose.prod.yml:88).
>    - `updateIntervalSeconds: 30`이 설정돼 있습니다(dashboards.yml:7).
>    - 배포 때 `git reset --hard`로 저장소를 갱신합니다(deploy-ec2.sh:34).
>    - deploy.sh는 api만 다시 띄우고 grafana는 건드리지 않습니다(:4, :13-14).
>    - backend-cd의 paths에 `infra/compose/**`가 들어 있습니다(:8).
>    - 로컬 compose도 같은 프로비저닝 폴더를 마운트합니다(docker-compose.yml:75).
>    - 저장소에서 옛 제목 `J-Bank 이체 성능`을 쓰는 곳은 대시보드 JSON 한 곳뿐이라, 제목을 바꿔도 다른 문서가 낡지 않습니다.
> 5. **커밋 계획**: atomic합니다. 대시보드 JSON, 문서, 개발일지, 작업 기록, 후속 운영 확인 기록이 각각 따로 나뉘어 있습니다. 제목 변경은 사용자가 패널 추가 때문에 결정한 것이라 커밋 1에 함께 넣어도 됩니다.
>
> ### 적용 제외 항목의 타당성
> - SEC: 권한·노출 경로 변경이 없고, uri 라벨에 개인정보가 없습니다.
> - BE·FE·E2E: 앱 코드 변경이 없습니다.
> - 성능: 요청 처리 경로 변경이 없습니다.
> - OPS-02·04·05·06·08: CI, 기동, 리소스, 백업 변경이 없습니다.
> - 모두 타당합니다. 다만 OPS-07에 알림 제외를 적는 것은 권고-4를 따릅니다.
>
> ### 이전 지적
> 최초 회차라 없습니다.
>
> 최종 판정: 수정 필요

### 메인 처리 내역

- 중요-1: 재점검 항목에 오류율 계산 형태(`(오류 합 or 0 * 전체 합) / 전체 합`, 전체 합계)와 0/값 없음 구분을 기록. REQ-05에 "요청 있고 오류 없으면 0, 요청 없으면 값 없음, 트래픽 있는데 5xx 계열이 비면 실패" 명시. REQ-07·AC-02를 쿼리별 기대값(p95 계열 숫자·제외 라벨 없음, 5xx 단일 계열 0, 4xx 단일 계열 0 초과)으로 분리.
- 권고-1: REQ-07에 `[5m]` 고정 창, 5초 간격 약 90초 호출, NaN 실패 기준 명시. REQ-02에 쿼리 4개, 반영 지연 p95의 NaN 허용 조건 명시.
- 권고-2: 재점검·REQ-04에 GC 비율(%)과 최대 멈춤(초) 단위 분리, AC-01에 계열별 단위 검사와 기준 커밋(`git show eda2d13:<파일>`) 추가.
- 권고-3: API별 p95에서 `UNKNOWN`·`/**` 제외를 표시 범위 판단으로 기록(4xx 비율에는 포함), REQ-03 갱신.
- 권고-4: OPS-07 행에 알림 제외(사용자 결정), 404로만 의도한 오류 생성, 5xx는 값 0 확인까지인 한계 기록. REQ-07에 없는 경로 404 호출 추가.
- 권고-5: 재점검 항목에 병합 시 api 재기동과 병합 시점은 사용자가 정한다는 점 기록.

## 2회차

회차 계획 지문: 981c5dd84b60d6953039da242dcc000b7f6216324a9f23241e36e3a57384b2c3 (2026-10-02T00:39:34Z 호출, verifier agentId a3a0de5148b7349dc)
회차 판정: 수정 필요

### verifier 응답 원문

(인용 표시 `> `만 붙이고 내용 그대로 보존)

> ## 계획 리뷰 2회차(재리뷰 1/2): ops-dashboard-panels
>
> ### 검증 대상
> - `.claude/tasks/ops-dashboard-panels/task.md`: 1회차 지적을 반영한 판, 명세 상태 확정
> - 1회차 원문과 처리 내역: `.claude/tasks/ops-dashboard-panels/plan-review.md`
> - 작업 시작 커밋: eda2d13
>
> ### 읽은 자료
> - 작업 문서: task.md, plan-review.md
> - 앱 코드
>   - `apps/jbank-api/src/main/java/com/jbank/auth/config/SecurityConfig.java`
>   - `apps/jbank-api/src/main/java/com/jbank/global/exception/GlobalExceptionHandler.java`
>   - `CsrfDoubleSubmitFilter.java`, `PrometheusScrapeRequestMatcher.java`: grep으로 확인
>   - `apps/jbank-api/src/main/resources/application.yml` 40-73행
> - 인프라
>   - `infra/compose/Caddyfile`
>   - `infra/compose/docker-compose.prod.yml`: MANAGEMENT_SERVER_PORT 행
>   - `infra/compose/perf/loadgen/provisioning/dashboards/json/jbank-perf.json`: expr 전체
> - 기준 문서(testing-policy, devops.md)와 그 밖의 인프라 파일은 1회차에서 이미 대조했습니다. 이번에는 바뀐 부분과 회귀만 확인했습니다.
> - 적용 기준 ID: OPS-01, OPS-03, OPS-07
>
> ### 확인하지 못한 범위
> - task.md:12의 운영 Prometheus 조회 결과(라벨과 status 목록)는 다시 확인하지 못했습니다. 1회차처럼 사실로 두고 검토했습니다.
> - 미인증 401의 uri가 `UNKNOWN`으로 기록된다는 점은 운영 관찰(task.md:12)에 근거합니다. 실행으로 확인한 것은 아닙니다.
> - 아래 대안 경로가 실제로 404와 uri `/**`로 기록되는지도 코드로 추론한 것이고, 실행하지 않았습니다.
>
> ### 1회차 지적별 처리
> - **중요-1: 해결.**
>   - 재점검 27행에 `(오류 합 or 0 * 전체 합) / 전체 합`을 `by` 없이 전체 합계로 쓴다고 적혀 있습니다.
>   - REQ-05(52행)에 세 가지 동작이 적혀 있습니다: 요청이 있고 오류가 없으면 0, 요청이 없으면 값 없음, 트래픽이 있는데 5xx 계열이 비면 실패.
>   - REQ-07(54행)에 쿼리별 기대값이 나뉘어 있습니다: p95 계열 숫자, 5xx 단일 계열 0, 4xx 단일 계열 0 초과.
>   - 모두 검증할 수 있는 형태입니다.
> - **권고-1: 해결.**
>   - REQ-07에 `[5m]` 고정 창, 5초 간격 약 90초 호출, "그 밖의 NaN은 실패"가 들어갔습니다.
>   - REQ-02에 쿼리 4개와 반영 지연 p95의 NaN 허용 조건이 명시됐습니다.
> - **권고-2: 해결.**
>   - REQ-04와 재점검 29행에서 GC 단위를 %와 초로 나눴습니다.
>   - AC-01에 계열별 단위 검사와 `git show eda2d13:<파일>` 기준점이 들어갔습니다.
> - **권고-3: 반영됨.** REQ-03과 28행에 결정과 이유가 있습니다. 타당성은 아래 2번에 적었습니다.
> - **권고-4: 반영됨.** OPS-07 행(100행)에 알림 제외와 5xx 한계가 적혔습니다. 다만 "안전한 404" 부분은 사실과 다릅니다. 아래 중요-A를 참고하세요.
> - **권고-5: 해결.** 30행에 병합 시점은 사용자가 정한다고 적혔습니다.
>
> ### 이번 회차 확인 결과
>
> **1. REQ-03에서 `UNKNOWN`·`/**`를 사용자에게 묻지 않고 뺀 판단은 타당합니다.**
> - 두 라벨은 업무 API 엔드포인트가 아닙니다.
>   - `UNKNOWN`은 보안 필터 단계에서 거부된 요청(401/403)입니다.
>   - `/**`는 매핑 없는 경로입니다.
> - "API별 지연"이라는 원문 요청의 의미에서 바로 나오는 표시 범위입니다.
> - 이 요청들은 4xx 비율(REQ-05)에 그대로 남아서 관측에서 빠지지 않습니다.
> - Q-02 답변("4xx를 따로 본다")과도 충돌하지 않습니다.
> - 제품 동작을 바꾸는 선택이 아니므로 질문을 생략해도 됩니다.
>
> **2. REQ-07의 `/api/v1/no-such-path`는 404가 아니라 401(uri `UNKNOWN`)이 됩니다.**
> - 코드 근거
>   - `SecurityConfig.java:28-40`의 PUBLIC_PATHS에 `/api/v1/no-such-path`와 일치하는 패턴이 없습니다. `/api/v1/products`는 정확히 일치하는 경로만 허용합니다.
>   - 그래서 `SecurityConfig.java:64-65`의 `anyRequest().authenticated()`에 걸립니다.
>   - 미인증 요청은 DispatcherServlet에 도달하기 전에 AuthorizationFilter에서 거부되고, `RestAuthenticationEntryPoint`가 401을 반환합니다(`:66-69`).
>   - `GlobalExceptionHandler.java:43-48`의 404 처리는 DispatcherServlet 안에서만 동작하므로 이 요청에는 닿지 않습니다.
>   - 외부 경로는 `Caddyfile:3-5`에서 `api:8080`으로 그대로 전달되므로 결과는 같습니다.
> - 기대값과의 관계
>   - 401도 4xx이므로 "4xx 비율 0보다 큼"은 그대로 성립합니다.
>   - 하지만 REQ-07, AC-02, OPS-07이 적은 "404(uri `/**`)를 만든다"는 사실과 다릅니다.
>
> ### 지적 사항
>
> **중요-A: REQ-07·AC-02·OPS-07의 "없는 경로 404"는 코드상 401(uri `UNKNOWN`)입니다.** 기준: REQ-07, OPS-07
> - 근거
>   - task.md:54(REQ-07): "없는 경로 `GET /api/v1/no-such-path`(404)"
>   - task.md:61(AC-02): "없는 경로 404"
>   - task.md:100(OPS-07): "의도한 오류는 안전한 404로만 만들고"
>   - 실제 응답은 위 확인 결과 2번처럼 401입니다(SecurityConfig.java:28-40, 64-69). GlobalExceptionHandler.java:43-48에는 도달하지 않습니다.
>   - task.md:26의 재점검 문장("인증 없는 요청의 401(uri `UNKNOWN`)")과도 서로 맞지 않습니다.
> - 예상 영향
>   - 실행하면 prod-query.md에 status 401과 uri `UNKNOWN`이 남습니다.
>   - 그러면 결과 리뷰에서 REQ-07 본문(404)과 증거가 어긋나 판정이 임의 해석에 맡겨집니다.
>   - REQ-03의 `/**` 제외도 이 절차로는 실제로 시험되지 않습니다. 운영에 이미 있는 탐색 요청이 5분 창 안에 우연히 있을 때만 시험됩니다.
>   - 숫자 기대값(4xx > 0)은 성립하므로 데이터 유실이나 운영 위험은 없습니다. 증거와 명세가 불일치하는 문제입니다.
> - 필요한 수정(둘 중 하나)
>   - (a) REQ-07·AC-02·OPS-07의 설명을 "미인증 없는 경로 → 401(uri `UNKNOWN`)"으로 고칩니다. 기대값에 "p95 결과에 `UNKNOWN` 계열 없음(REQ-03 제외 확인)"을 추가합니다. 이 경우 `/**` 제외를 직접 시험하지 않는 점은 한계로 적습니다.
>   - (b) 404와 `/**`를 실제로 만들고 싶으면 PUBLIC_PATHS 안에 있으면서 매핑이 없는 경로를 씁니다.
>     - 예: `GET /actuator/health/no-such`. 운영은 `MANAGEMENT_SERVER_PORT: "9095"`(docker-compose.prod.yml:38)라서 본 포트에는 actuator 매핑이 없습니다. 따라서 NoResourceFoundException이 발생하고 404가 될 것으로 추론합니다.
>     - 미인증 401 요청과 함께 보내면 `UNKNOWN`과 `/**` 제외를 모두 시험할 수 있습니다.
>     - 다만 이 404는 실행으로 확인하지 않았습니다. 결과 기록에 실제 status와 uri를 남겨야 합니다.
>
> **권고-A: 쿼리 실행 시점.**
> - 근거: task.md:54는 `[5m]` 창이지만 "보낸 뒤" 언제 조회하는지가 없습니다.
> - 필요한 수정: 마지막 외부 요청 뒤 5분 안에 조회한다는 조건을 REQ-07에 한 줄 추가하면 판정이 분명해집니다. 차단 사유는 아닙니다.
>
> **참고(조치 불필요)**
> - 운영에서는 actuator가 관리 포트에 있습니다. 그래서 본 포트의 `/actuator/...` 요청은 uri가 `/**`로 기록되고, `/actuator/**` 라벨 제외는 실제로 걸러 내는 것이 없습니다. 해가 없으므로 유지해도 됩니다.
>
> ### 새 모호함과 불일치 점검
> - REQ-01~09와 AC-01~04의 연결은 유지됩니다. AC-01이 REQ-04의 단위 검사를 덮는 연결(60행)도 추가돼 일관됩니다.
> - 기준 ID 표(OPS-01·03·07)와 REQ·AC가 서로 맞습니다. 위 중요-A의 404 표기만 예외입니다.
> - REQ-05의 "트래픽이 있는데 5xx 계열이 비면 실패"와 REQ-07의 "5xx 단일 계열 값 0"이 서로 맞습니다.
> - 그 밖에 새로 생긴 임의 가정은 없습니다.
>
> ### 적용 제외 항목
> 1회차 판단에서 바뀐 것이 없습니다. OPS-02·04·05·06·08, SEC, BE, FE, E2E, 성능을 제외한 판단은 모두 타당합니다.
>
> 최종 판정: 수정 필요

### 메인 처리 내역

- 중요-A: (b)안 채택. 404·uri `/**`는 2026-10-02 운영에서 이미 관찰한 `GET /actuator/health`(prod-metrics-scrape logs/prod-check.md: 404 `COMMON_004_NOT_FOUND`, 같은 날 uri 라벨 조회에서 `/**` 404)로 만들고, 401·uri `UNKNOWN`은 `GET /api/v1/no-such-path`로 만든다. REQ-07·AC-02·OPS-07에 각 경로의 실제 status·uri를 명시하고, p95 결과에 `UNKNOWN`·`/**`가 없음을 기대값에 추가. 기록에 실제 status·uri를 남긴다고 OPS-07에 명시.
- 권고-A: REQ-07에 "마지막 요청 뒤 5분 안에 조회" 추가.

## 3회차

회차 기준: 계획 리뷰 한도(최초 1회+재리뷰 2회)의 마지막 회차. 2026-10-02T00:42:20Z 호출, verifier agentId ae14fe98c5606130f.
대상 계획 지문: f45a5dd096d340a472f57e1e5c941ea4467744660dc53a94f4b3a14d0f52af22
최종 판정: 통과 권고

### verifier 응답 원문

(인용 표시 `> `만 붙이고 내용 그대로 보존)

> ## 계획 리뷰 3회차(재리뷰 2/2, 마지막 회차): ops-dashboard-panels
>
> ### 검증 대상
> - `/Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/worktrees/wizardly-proskuriakova-fd2557/.claude/tasks/ops-dashboard-panels/task.md`: 2회차의 중요-A와 권고-A를 반영한 판이며 명세 상태는 확정입니다.
> - 이전 회차 원문과 처리 내역: `.claude/tasks/ops-dashboard-panels/plan-review.md`
>
> ### 읽은 자료
> - task.md 전체(1~126행)
> - plan-review.md 전체(1·2회차 원문과 메인 처리 내역)
> - `.claude/tasks/prod-metrics-scrape/logs/prod-check.md`: 37·78·84행
> - `apps/jbank-api/src/main/java/com/jbank/auth/config/SecurityConfig.java`: PUBLIC_PATHS(28~38행), 인가 규칙(58~65행)
> - 적용 기준 ID: OPS-01, OPS-03, OPS-07
> - 기준 문서(testing-policy, devops.md)와 그 밖의 인프라 파일은 1·2회차에서 대조를 마쳤습니다. 이번에는 다시 읽지 않고 바뀐 부분과 회귀만 확인했습니다.
>
> ### 확인하지 못한 범위
> - 외부 `GET /actuator/health` 404의 uri 라벨이 `/**`라는 점은 직접 확인하지 못했습니다. 근거는 두 가지인데, 둘 다 같은 날 관찰한 것이고 서로를 직접 연결한 증거는 없습니다.
>   - 그 요청의 응답이 404 `COMMON_004_NOT_FOUND`였다는 기록(prod-check.md:37)
>   - 같은 날 Prometheus 조회에서 uri `/**`, status 404 계열이 있었다는 기록(task.md:12)
>   - 이 경로는 `/actuator/health/**`(SecurityConfig.java:36)에 걸려 permitAll이 됩니다. 운영 본 포트에는 actuator 매핑이 없으므로 NoResourceFoundException이 발생하고 uri가 `/**`가 된다는 추론은 타당합니다. 다만 실행해서 확인한 것은 아닙니다.
> - 운영 Prometheus 조회 결과(task.md:12)는 1·2회차와 마찬가지로 사실로 두고 검토했습니다.
>
> ### 이전 지적별 처리
> - **중요-A: 해결.**
>   - 메인은 (b)안을 택했습니다. 404와 uri `/**`는 `GET /actuator/health`로, 401과 uri `UNKNOWN`은 `GET /api/v1/no-such-path`로 만듭니다.
>   - 이 내용이 반영된 곳:
>     - REQ-07(task.md:54)
>     - AC-02(task.md:61)
>     - OPS-07(task.md:100)
>   - 세 곳의 status와 uri 표기가 서로 맞습니다.
>   - 코드 근거와도 맞습니다.
>     - `/actuator/health`는 PUBLIC_PATHS에 해당해 permitAll이 되고, 이어서 매핑이 없어 404가 납니다(SecurityConfig.java:36, prod-check.md:37).
>     - `/api/v1/no-such-path`는 `anyRequest().authenticated()`(SecurityConfig.java:64-65)에 걸려 401이 납니다.
>   - 기대값에 "p95 결과에 `UNKNOWN`·`/**`·상태 확인 경로 계열 없음"이 추가되었습니다. 이제 REQ-03의 두 제외 라벨을 실제 요청으로 시험할 수 있습니다.
>   - OPS-07에 "기록에 실제 status·uri를 남긴다"가 명시되었습니다.
>   - 두 요청 모두 GET이고, 각각 404 처리기와 인증 진입점에서 끝나므로 운영 데이터에 쓰기가 없습니다.
> - **권고-A: 해결.** REQ-07(task.md:54)에 "마지막 요청 뒤 5분 안에 조회"가 들어가 `[5m]` 창과 맞습니다.
> - 1회차 지적(중요-1, 권고-1~5)은 2회차에서 해결 확인을 마쳤습니다. 이번 수정으로 생긴 회귀는 없습니다. 다음 항목이 모두 그대로 유지됩니다.
>   - REQ-05의 0과 "값 없음" 구분
>   - REQ-02의 NaN 허용 범위
>   - GC 단위 분리
>   - AC-01 기준 커밋
>
> ### 이번 회차 새 불일치·모호함 점검
> - REQ-07, AC-02, OPS-07과 재점검 항목(26·28행)의 401/`UNKNOWN`, 404/`/**` 표기가 서로 맞습니다.
> - 4xx 비율의 분모에서 빼는 경로는 `/readyz`·`/livez`·`/actuator/**`입니다. 운영 본 포트의 `/actuator/health` 요청은 uri `/**`로 기록되므로 4xx 비율에는 그대로 들어갑니다. 따라서 "4xx 단일 계열 0 초과" 기대와 충돌하지 않습니다.
> - REQ와 AC의 연결(REQ-01~09 ↔ AC-01~04)과 기준 ID 표가 그대로 유지됩니다.
>
> ### 지적 사항
> - 차단: 없음
> - 중요: 없음
> - **권고-B(선택, 차단 아님)**
>   - 근거: task.md:54는 `/actuator/health`의 uri를 `/**`로 단정합니다. 하지만 위 "확인하지 못한 범위"처럼 이 요청과 `/**` 라벨이 직접 연결된 증거는 없습니다.
>   - 영향: 실행 결과 uri가 `/**`가 아니면 "p95에 `/**` 없음"이라는 기대가 저절로 통과합니다. 그러면 REQ-03의 `/**` 제외를 실제로는 시험하지 못합니다.
>   - 필요한 수정: 다음 문장을 OPS-07이나 REQ-07에 한 줄 추가하면 결과 리뷰의 판정이 분명해집니다. "실제 uri가 `/**`가 아니면 그 값을 기록하고, `/**` 제외는 미시험 한계로 남긴다." 실행 기록을 남기는 의무는 이미 있으므로 반영하지 않아도 완료를 막지 않습니다.
>
> ### 적용 제외 항목
> 1·2회차 판단에서 바뀐 것이 없습니다. OPS-02·04·05·06·08, SEC, BE, FE, E2E, 성능을 제외한 판단은 모두 타당합니다. 알림 제외와 5xx를 실제로 만들지 못하는 한계는 OPS-07 행(task.md:100)에 기록되어 있습니다.
>
> 최종 판정: 통과 권고

### 메인 처리 내역

- 권고-B: task.md는 계획 지문 유지를 위해 바꾸지 않는다. 실행 시 Prometheus에서 `/actuator/health` 요청 전후 `uri="/**",status="404"` 카운터 증가를 함께 조회해 그 요청이 `/**`로 기록됐는지 직접 확인하고, 아니면 실제 uri를 기록하고 `/**` 제외를 미시험 한계로 남긴다(logs/prod-query.md).

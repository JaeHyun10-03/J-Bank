# 검토 결과

## 검토 범위

결과 리뷰 1/3회차(배포 전 범위). 작업 시작 eda2d13 → dc6f348, 커밋 2개(커밋 계획 1·2). verify-001 통과 증거 기준.
REQ-08·AC-03(운영 배포 뒤 Grafana 반영·사용자 화면 확인)은 병합 뒤에만 확인할 수 있어 이 회차 범위 밖이다.

## 독립 검증 결과

- 호출: verifier, agentId a029668bcf3e74158, 2026-10-02(review-begin 1/3 직후).
- 대상 snapshot: 925e6e366ab7ff67daf3f69084df71f0ee491250302e1b4a0302cb974099d7a6
- 응답 원문(인용 표시 `> `만 붙이고 내용 그대로):

> ## 결과 리뷰 1/3회차: ops-dashboard-panels
>
> ### 검증 대상
> - snapshot: `925e6e366ab7ff67daf3f69084df71f0ee491250302e1b4a0302cb974099d7a6` (evidence/checks.json, git head `dc6f348`, dirty false)
> - 변경 파일: evidence/changes.txt 기준 2개입니다. 대시보드 JSON(+181/-6)과 `docs/06_J-Bank_인프라아키텍처.md`(+11)입니다.
>
> ### 읽은 자료
> - docs/testing-policy.md, docs/testing/devops.md
> - task.md, plan-review.md(1~3회차와 처리 내역), progress.md
> - `infra/compose/observability/provisioning/dashboards/json/jbank-transfer.json`(현재본)
> - 기준본: git을 실행할 수 없어서 메인 체크아웃의 같은 경로 파일(제목 `J-Bank 이체 성능`, 패널 5개)을 eda2d13 상태로 보고 대조했습니다.
> - perf 대시보드 `jbank-perf.json`(쿼리 원본), `dashboards.yml`, docker-compose(.prod).yml의 Grafana 이미지와 마운트
> - logs/dashboard-check.txt, logs/prod-query.md
> - evidence/checks.json, evidence/changes.txt, verify-001/check-5.log
> - 인프라 문서 3.5절
>
> ### 확인하지 못한 범위
> - 명령은 실행하지 않았습니다. 따라서 snapshot 해시 재계산, `git show eda2d13`, 구조 검사 재실행, 로컬 Grafana와 운영 Prometheus 결과의 진위는 로그 내용으로만 판단했습니다.
> - 운영 쿼리에 실제로 보낸 식 문자열과 URL 생성 코드는 로그에 없습니다. 실제로 보낸 식이 JSON 식과 같은지는 결과의 형태로만 간접 확인했습니다.
> - REQ-08과 AC-03은 배포 후 대기입니다. 증거가 없고 이번 판정 범위에서 뺐습니다.
> - 개발일지와 작업 기록 커밋(커밋 계획 3·4)은 아직 없습니다. 완료 전에 필요합니다.
>
> ### REQ별 대조
>
> | REQ | 구현 위치 | 쿼리·단위 대조 | 증거 | 판정 |
> | --- | --- | --- | --- | --- |
> | REQ-01 | JSON 2~3행, 패널 1~5 | 제목 `J-Bank 운영`, uid는 그대로입니다. 기준본과 대조하니 패널 1~5의 title, expr, fieldConfig(단위, 패널 3의 min·max 포함)가 같고 gridPos y만 +1입니다. | dashboard-check 91~95행 OK | 충족 |
> | REQ-02 | 패널 6·7·8 | 다음 쿼리 4개가 perf 패널 20~22와 같은 형태이고 job만 `jbank-api`로 바꿨습니다. 단위는 6 short, 7·8 s입니다.<br>- `sum(jbank_credit_pending_count{job="jbank-api"})`<br>- `max(..._oldest_seconds)`<br>- `histogram_quantile(0.95, sum by (le)(rate(..._bucket)))`<br>- `max(..._max)` | prod-query 결과는 건수 0, 나이 0, max 0이고 p95는 NaN입니다. 기준대로 p95는 NaN을 허용합니다. | 충족 |
> | REQ-03 | 패널 9 | JSON의 `uri!~\"/readyz\|/livez\|/actuator.*\|UNKNOWN\|/\\\\*\\\\*\"`는 PromQL 문자열 `"…\|/\\*\\*"`이 되고, 정규식으로는 `/\*\*`(리터럴 `/**`)입니다. Prometheus는 정규식 전체를 앵커하므로 5종이 정확히 일치할 때만 빠집니다. `/actuator.*`는 `/actuator/**`를 포함합니다. `sum by (le, method, uri)`, 단위 s입니다. | 같은 5분 창에서 UNKNOWN +3과 `/**` +3이 증가했고(전후 카운터로 확인), 결과에는 `GET /api/v1/products` 0.0073초 계열 하나뿐입니다. 제외가 실제로 동작함을 보여 줍니다. | 충족 |
> | REQ-04 | 패널 11·12·13 | 다음 3개 패널로 perf 패널 9·10을 단위별로 나눴습니다.<br>- 패널 11(bytes): `sum(jvm_memory_used_bytes{area="heap"})`와 max<br>- 패널 12(percentunit): `sum(rate(jvm_gc_pause_seconds_sum))`. 초/초라서 비율 단위가 맞습니다.<br>- 패널 13(s): `max(jvm_gc_pause_seconds_max)` | used 70,520,720, max 536,870,910, GC 비율 6.7e-5, 최대 멈춤 0.019 | 충족 |
> | REQ-05 | 패널 10 | `(A or 0 * B) / B`이고 `or`의 우선순위가 가장 낮아 `(A or (0*B)) / B`로 계산됩니다. `by`가 없어서 라벨 없는 단일 계열입니다. 분모와 분자 모두 `uri!~"/readyz\|/livez\|/actuator.*"`이고 UNKNOWN과 `/**`는 포함됩니다(Q-02, 재점검 26행과 일치). `status=~"5.."`·`"4.."`, 단위 percentunit입니다. 요청이 없으면 0/0=NaN 또는 빈 결과라서 "값 없음"과 일치합니다. | 5xx 단일 계열 0, 4xx 단일 계열 0.283 | 충족 |
> | REQ-06 | 전체 | id 17개가 고유합니다(행 100~103, 패널 1~13). gridPos를 직접 계산해도 겹침이 없습니다(행 y 0·25·34·43, 패널 1~24·26~33·35~42·44~51). 새 패널과 타깃의 datasource uid는 모두 `prometheus`입니다. | 구조 검사 통과(종료 코드 0). 로컬 Grafana 11.3.1(운영 compose와 같은 버전과 마운트)에서 title, uid, panels 17, provisioned True, `finished to provision dashboards` 확인 | 충족 |
> | REQ-07 | - | `[5m]` 창, products 200 18회(5초 간격, 약 92초), health 404 3회, no-such-path 401 3회. 조회는 마지막 요청 약 45초 뒤였습니다. | 11개 쿼리 모두 success. 허용된 p95 외에 NaN은 없습니다. 기대값 각 항목을 충족합니다. 계획 권고-B는 `/**` 404 카운터 1→4로 직접 확인했습니다. | 충족 |
> | REQ-08 | - | - | 없음 | 배포 후 대기 |
> | REQ-09 | 문서 83~92행 | 제목, uid, 파일, 30초 재로드, 행 4개와 패널 구성, 제외 규칙, 4xx 포함, 0과 빈 그래프 구분, 알림 없음이 JSON과 일치합니다. 이체·DB 행의 "5xx 비율"은 패널 3의 `outcome="SERVER_ERROR"`와 같은 의미입니다. | 문서 원문 | 충족 |
>
> 4xx 0.283의 타당성도 확인했습니다. 단순 계산 6/24=0.25보다 약간 높은데, 새로 생긴 products 계열은 첫 수집 값이 증가분에서 빠지기 때문이라고 설명됩니다. 만약 `/readyz`가 분모에 섞였다면 값은 오히려 더 낮아졌을 것입니다. 따라서 제외 동작과 모순되지 않습니다.
>
> ### 기준 ID 판정
> - **OPS-01: 충족.** 저장소 프로비저닝 폴더를 읽기 전용으로 마운트하기만 해서 로드했고 수동 설정은 없습니다. 이미지 태그도 운영과 같은 11.3.1입니다.
> - **OPS-03: 충족.** 구조 검사 스크립트 원문과 출력, 종료 코드가 로그에 있습니다. 검사 항목은 JSON 파싱, id 고유, 겹침, datasource uid, 기존 패널 불변, 계열별 단위, job 선택자, 제외 라벨, 오류율 식 형태입니다.
> - **OPS-07: 병합 전 부분은 충족, 배포 후 부분은 대기.** 의도한 404·401 요청을 보냈고 실제 status와 uri, 카운터 전후 값이 기록되어 있습니다. 알림 제외와 5xx를 0까지만 확인한 한계는 계획에서 합의했고 기록에도 남아 있습니다. 배포 뒤 Grafana 프로비저닝 오류 0건은 미확인입니다.
> - **적용 제외(OPS-02·04·05·06·08, SEC, BE, FE, E2E, 성능): 타당합니다.** 변경은 대시보드 JSON과 문서뿐입니다. verify의 gradle test는 UP-TO-DATE였는데, 앱 코드 변경이 없으니 맞는 결과입니다. checks.json의 명령은 실제 앱 검사(unittest, lint, tsc, jest, gradle test·spotless, next build)이고 6개 모두 exit 0입니다.
>
> ### 로컬 Grafana `level=error` 2건에 대한 메인 판단
> 타당합니다. 두 오류는 `provisioning.plugins`와 `provisioning.alerting` 로거가 `/etc/grafana/provisioning/{plugins,alerting}` 디렉터리가 없다고 남긴 것입니다. 저장소 provisioning 폴더에는 `dashboards`·`datasources`만 있습니다(Glob으로 확인). 대시보드 JSON 내용과는 관계가 없습니다. 대시보드 로더(`provisioning.dashboard`)는 info 2줄만 남겼고, API 조회에서도 17개 패널이 로드됐습니다.
>
> ### 지적 사항
> - 차단: 없음
> - 중요: 없음
> - **권고-1 (AC-03 사전 기준, OPS-07).** 운영 Grafana도 같은 마운트라서 plugins·alerting `level=error` 2건이 그대로 나올 것입니다. AC-03의 "프로비저닝 오류 grep"을 `logger=provisioning.dashboard`의 error·warn으로 한정하고, 위 2건은 기존 무관 항목이라고 prod-check.md에 적어야 합니다. 그래야 배포 후 판정이 흔들리지 않습니다.
> - **권고-2 (AC-02 증거 재현성).** prod-query.md에는 실제로 보낸 쿼리 식이나 생성 스크립트가 없고 "JSON에서 `$__rate_interval`만 `5m`으로 바꿨다"는 설명만 있습니다. 결과 형태(제외 라벨이 빠진 단일 계열, `or 0 *` 동작)를 보면 식이 일치한다고 볼 수 있어서 완료를 막지는 않습니다. 다만 개발일지나 작업 기록 커밋 때 생성 명령 한 줄을 로그에 덧붙이면 재현할 수 있습니다.
> - **권고-3 (표시 품질, 선택).** 패널 11의 힙 max는 `sum(jvm_memory_max_bytes{area="heap"})`입니다. G1 Eden·Survivor 풀의 max가 -1이라 합계가 2바이트 작습니다(로그에 이미 기록됨). perf와 같은 식이고 영향이 미미하므로 수정은 필요 없습니다. 바꾸려면 `jvm_memory_max_bytes{...} > 0`으로 걸러 더하면 됩니다.
>
> ### 이전 지적
> 결과 리뷰는 최초 회차라 해당하는 지적이 없습니다. 계획 리뷰의 중요-1·중요-A와 권고-1~5·A·B는 구현과 증거에 반영되어 있습니다. 반영 내용은 오류율 `or 0 *` 식, GC 단위 분리, p95 제외 라벨, 401·404 경로, 5분 창, `/**` 카운터 전후 확인입니다.
>
> ### 남은 일 (완료 전)
> - REQ-08과 AC-03: 배포 뒤 logs/prod-check.md에 기록하고 사용자 화면 확인을 받아야 합니다.
> - 커밋 3(개발일지)·4(작업 기록)
> - 이 결과의 원문을 review.md에 보존해야 합니다.
>
> 최종 판정: 통과 권고(배포 전 범위)

## 요구사항별 검증

| 요구사항 ID | 구현 위치 | 테스트·실행 증거 | 판정 |
| --- | --- | --- | --- |
| REQ-01 | jbank-transfer.json 제목·패널 1~5 | logs/dashboard-check.txt 구조 검사(기존 패널 불변) | 통과 |
| REQ-02 | 패널 6·7·8 | logs/prod-query.md(건수·나이·max 0, p95 NaN 허용) | 통과 |
| REQ-03 | 패널 9 | logs/prod-query.md(products 단일 계열, UNKNOWN·`/**` 제외 확인) | 통과 |
| REQ-04 | 패널 11·12·13 | logs/dashboard-check.txt 단위 검사, logs/prod-query.md 값 | 통과 |
| REQ-05 | 패널 10 | logs/prod-query.md(5xx 0, 4xx 0.283) | 통과 |
| REQ-06 | 전체 JSON | logs/dashboard-check.txt(구조 검사, 로컬 Grafana 11.3.1 로드) | 통과 |
| REQ-07 | 운영 쿼리 절차 | logs/prod-query.md | 통과 |
| REQ-08 | 배포 후 | 증거 없음 | 배포 후 대기 |
| REQ-09 | docs/06 3.5절 | verifier 대조 | 통과 |

## 지적별 처리

- 차단·중요: 없음.
- 권고-1: 배포 뒤 AC-03 확인은 `logger=provisioning.dashboard`의 error·warn만 판정 대상으로 하고, plugins·alerting 폴더 없음 2건은 기존 무관 항목으로 prod-check.md에 적는다.
- 권고-2: logs/prod-query.md에 쿼리 생성 명령 절 추가(작업 기록 폴더라 snapshot 영향 없음).
- 권고-3: perf와 같은 식이고 2바이트 차이라 수정하지 않는다.

## 완료 기준별 근거

- AC-01: logs/dashboard-check.txt
- AC-02: logs/prod-query.md
- AC-03: 배포 후 대기
- AC-04: verifier 대조
- 하네스 verify: verify-001 통과(앱 코드 변경 없어 Gradle UP-TO-DATE)

## 성능 테스트 확인

불필요(task.md 성능 테스트 절). 앱 요청 처리 변경 없음.

## 발견한 문제

- 운영의 `UNKNOWN` 401이 작업 시작 전 이미 68건이었다. 인터넷 탐색 요청으로 보이며, 4xx 비율에 그대로 잡힌다(Q-02 결정대로).

## 판정과 이유

review fail(1/3). 배포 전 범위는 차단·중요 지적 없이 통과 권고를 받았지만 REQ-08 운영 증거가 없어 통과로 등록할 수 없다. 병합·배포 뒤 logs/prod-check.md를 남기고 결과 리뷰 2회차에서 통과를 등록한다.

## 확인하지 못한 부분

- 운영 배포 뒤 Grafana 반영과 사용자 화면 확인(REQ-08·AC-03).
- verifier는 명령을 실행하지 않았고 로그를 읽어 판정했다.

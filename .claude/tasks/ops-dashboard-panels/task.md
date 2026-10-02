# 작업 명세

## 요구사항 구체화

명세 상태: 확정
원문 요청: 운영 대시보드 jbank-transfer에 패널 추가: 입금 반영 상태(미반영 건수·가장 오래된 미반영 나이·반영 지연 p95), API별 지연시간 p95, JVM 힙·GC, 전체 API 에러율. 알림 제외.
(2026-10-01 prod-metrics-scrape 대화에서 사용자가 네 패널을 모두 선택, 알림 규칙은 "많이 나중에"로 보류)
요청 해석: 운영 Grafana의 프로비저닝 대시보드(`infra/compose/observability/provisioning/dashboards/json/jbank-transfer.json`)에 네 묶음의 패널을 더해, 운영에서 입금 반영 적체·API별 지연·메모리·오류를 한 화면에서 보게 한다. 앱 코드·지표·알림은 바꾸지 않는다.
조사한 사실:
- 현재 대시보드: 제목 `J-Bank 이체 성능`, uid `jbank-transfer`, 패널 5개(이체 p50/95/99, 이체 TPS, 이체 5xx 비율, Hikari 커넥션 풀, 계좌 락 대기). 쿼리에 job 선택자가 없다. 운영(prometheus.prod.yml)과 로컬(prometheus.yml) 모두 job 이름이 `jbank-api`이고 같은 프로비저닝 폴더를 쓴다.
- perf 대시보드(`infra/compose/perf/loadgen/.../jbank-perf.json`, job `api`)에 같은 성격의 검증된 쿼리가 있다: 입금 반영 대기 수·가장 오래된 미반영 나이·반영 지연 p95/max, API p95 by (method, uri), 5xx 비율, JVM 힙 used/max, GC 멈춤 시간 비율·max.
- 2026-10-02 운영 Prometheus 읽기 전용 조회(SSM 4becb910-b2db-4195-b711-cc0300ad3e10): `jbank_credit_pending_count`, `jbank_credit_pending_oldest_seconds`, `jbank_credit_apply_lag_seconds_bucket/max`, `jvm_memory_used_bytes`, `jvm_memory_max_bytes`, `jvm_gc_pause_seconds_sum/max`, `http_server_requests_seconds_bucket/count`가 있다. uri 라벨은 `/**`(매핑 없는 경로 404), `/livez`, `/readyz`, `UNKNOWN`(보안 필터 401)뿐이고 아직 업무 API 트래픽이 없다. 관리 포트 스크랩(`/actuator/prometheus`)은 `http_server_requests`에 잡히지 않는다. status는 200·401·404.
- `/readyz`는 운영 프론트 서버 상태 확인이 주기적으로 부르고 `/livez`도 상태 확인용이라, 그대로 두면 API 지연·오류율 분모를 왜곡한다.
- Grafana는 `./observability/provisioning`을 디렉터리 바인드 마운트하고 `updateIntervalSeconds: 30`이라, 저장소 파일이 바뀌면 재시작 없이 30초 안에 반영된다(Prometheus의 단일 파일 마운트와 다름).
- backend-cd는 `infra/compose/**` 변경에도 돈다. 병합하면 이미지 재빌드와 api 재기동이 같이 일어난다(인스턴스가 꺼져 있으면 다음 부팅의 sync-latest가 저장소를 갱신).
질문하지 않은 이유: 아래 표의 두 가지만 물었다. 쿼리는 perf 대시보드의 검증된 형태를 job `jbank-api`로 옮기고, 상태 확인 경로(`/readyz`·`/livez`·`/actuator/**`) 제외는 위 사실(지표 왜곡)에서 바로 도출되는 사항이라 묻지 않았다. 패널 배치(행 구분)는 표시 방식이라 구현에서 정한다.

원문 요청의 첫 줄은 콜론 뒤에 적고, 길면 이어지는 줄에 계속 적는다. 개인정보·비밀값은 문서에 복사하지 않고 위치와 필요한 의미만 남긴다.

| 질문 ID | 결정 사항과 영향 | 사용자 답변·근거 | 상태 |
| --- | --- | --- | --- |
| Q-01 | 제목이 `J-Bank 이체 성능`이라 비이체 패널을 넣으면 이름과 내용이 어긋남. 제목만 변경 / 새 대시보드 분리 / 유지 | 2026-10-02 사용자: 제목만 `J-Bank 운영`으로 변경, 파일·uid 유지, 한 화면에 행으로 구분 | 해결 |
| Q-02 | 전체 API 에러율의 오류 기준: 5xx만 / 5xx와 4xx를 따로 | 2026-10-02 사용자: 5xx 비율과 4xx 비율을 따로 표시 | 해결 |

답변 후 재점검:
- 4xx에는 인증 없는 요청의 401(uri `UNKNOWN`)과 없는 경로 404(uri `/**`)가 포함된다. 인터넷에서 들어오는 탐색 요청도 섞일 수 있지만, 사용자 선택이 "4xx를 따로 본다"이므로 그대로 포함하고 패널 설명에 적는다.
- 오류율은 요청이 있는데 해당 오류가 없으면 0, 요청 자체가 없으면 값 없음(빈 그래프)으로 구분한다. 운영에는 5xx 계열이 아직 없어 단순 나눗셈이면 트래픽이 있어도 빈 그래프가 되므로, perf 패널 5와 같은 `(오류 합 or 0 * 전체 합) / 전체 합` 형태를 `by` 없이 전체 합계로 쓴다(계획 리뷰 1회차 중요-1). 지연 p95는 요청이 없으면 값이 없다.
- API별 p95 패널은 업무 API만 보이도록 상태 확인 경로와 함께 `uri="UNKNOWN"`(보안 필터 401)·`uri="/**"`(매핑 없는 경로 404)도 제외한다. 인터넷 탐색 요청이 "API별" 목록을 채우지 않게 하려는 표시 범위 판단이며, 이 요청들은 4xx 비율에는 그대로 포함된다(계획 리뷰 1회차 권고-3).
- GC 최대 멈춤은 초 단위다. perf 패널 10은 비율 패널에 섞어 %로 보이므로, 운영 대시보드에서는 GC 멈춤 시간 비율(%)과 최대 멈춤(초)을 단위가 맞게 표시한다(권고-2).
- 병합하면 backend-cd가 api를 재기동해 몇 초 끊긴다. 병합 시점은 사용자가 정한다(권고-5).
- 새 제목은 검색·목록 표시만 바뀌고 uid가 같아 기존 주소·북마크는 유지된다.

## 목표

운영 Grafana의 `J-Bank 운영` 대시보드 한 화면에서 이체 지표(기존)와 함께 입금 반영 적체, API별 p95 지연, JVM 힙·GC, 전체 API 5xx·4xx 비율을 본다.

## 범위

- `infra/compose/observability/provisioning/dashboards/json/jbank-transfer.json`: 제목 변경, 행 구분, 새 패널
- 인프라 문서 3.5절 대시보드 설명
- 운영 Prometheus에서 새 쿼리 검증(읽기 전용), 배포 뒤 Grafana 반영 확인
- 개발일지, 작업 기록

## 요구사항

| ID | 조건·입력 | 기대 동작·실패 조건 |
| --- | --- | --- |
| REQ-01 | 대시보드 프로비저닝 | 제목 `J-Bank 운영`, uid `jbank-transfer` 유지. 기존 5개 패널의 쿼리·단위는 그대로. 기존 패널이 사라지거나 쿼리가 바뀌면 실패 |
| REQ-02 | 입금 반영 패널 | 미반영 건수 합(`jbank_credit_pending_count`), 가장 오래된 미반영 나이 최대(초), 반영 지연 p95와 max(초)를 job `jbank-api` 기준으로 표시(쿼리 4개). 운영에서 건수·나이·max는 숫자 결과가 있어야 함. 반영 지연 p95는 이체가 없으면 NaN·빈 결과를 허용(문법 success만 확인) |
| REQ-03 | API별 지연 패널 | method·uri별 p95(초). `/readyz`·`/livez`·`/actuator/**`·`UNKNOWN`·`/**`는 제외. 해당 라벨이 결과에 섞이면 실패 |
| REQ-04 | JVM 패널 | 힙 사용량과 최대(바이트), GC 멈춤 시간 비율(%)과 최대 멈춤(초)을 각각 맞는 단위로 표시. 운영에서 숫자 결과가 있어야 함 |
| REQ-05 | 전체 API 오류율 패널 | 상태 확인 경로(`/readyz`·`/livez`·`/actuator/**`)를 뺀 전체 요청 대비 5xx 비율과 4xx 비율을 각각 단일 계열(전체 합계)로 표시. 요청이 있고 해당 오류가 없으면 0, 요청이 없으면 값 없음. 트래픽이 있는데 5xx 계열이 비면 실패 |
| REQ-06 | Grafana 11.3.1 프로비저닝 | 대시보드가 오류 없이 로드되고 패널이 서로 겹치지 않으며 패널 id가 고유. 모든 새 쿼리가 datasource uid `prometheus`를 씀 |
| REQ-07 | 운영 Prometheus에 새 쿼리 실행(병합 전) | `$__rate_interval` 대신 고정 창 `[5m]`으로 실행. 외부에서 미인증으로 `GET /api/v1/products`(200)를 5초 간격으로 약 90초, 같은 기간에 `GET /actuator/health`(404, uri `/**`. 2026-10-02 운영에서 관찰)와 `GET /api/v1/no-such-path`(보안 필터 401, uri `UNKNOWN`)를 몇 번 보내고, 마지막 요청 뒤 5분 안에 조회: 모든 새 쿼리 `success`. API p95는 `method="GET",uri="/api/v1/products"` 계열이 NaN 아닌 숫자이고 `UNKNOWN`·`/**`·상태 확인 경로 계열 없음. 5xx 비율은 단일 계열 값 0. 4xx 비율은 단일 계열 0보다 큰 값. 입금 반영·JVM은 REQ-02·04 기준. 그 밖의 NaN은 실패 |
| REQ-08 | 운영 배포 뒤 | 운영 Grafana에 `J-Bank 운영` 대시보드가 반영되고 Grafana 로그에 프로비저닝 오류가 없음. 사용자가 화면에서 새 패널을 확인 |
| REQ-09 | 문서 | 인프라 문서 3.5절이 대시보드 제목·패널 구성과 일치 |

## 완료 기준

- AC-01(REQ-01·04·06): 구조 검사 스크립트로 JSON 파싱, 패널 id 고유, gridPos 겹침 없음, 기존 5개 패널 쿼리·단위가 작업 시작 커밋(`git show eda2d13:<파일>`)과 동일, 새 쿼리 datasource uid `prometheus`, 새 계열별 단위(바이트·초·%·건수)가 기대와 일치. 로컬 Grafana 11.3.1 컨테이너에 프로비저닝 폴더를 마운트해 대시보드 API로 제목·패널 수 확인, 로그에 프로비저닝 오류 없음. 결과를 logs/dashboard-check.txt에 저장
- AC-02(REQ-02~05·07): REQ-07 절차대로 외부 요청(공개 읽기 `GET /api/v1/products` 200, `/actuator/health` 404, 없는 업무 경로 401. 모두 GET이라 운영 데이터 변경 없음) 뒤 운영 Prometheus에 새 쿼리를 `[5m]` 창으로 SSM 읽기 전용 실행. 쿼리별 status·계열 수·라벨·값을 logs/prod-query.md에 저장
- AC-03(REQ-08): 배포 뒤 SSM으로 Grafana 컨테이너 안 파일과 로그의 프로비저닝 오류 grep(읽기 전용), 사용자의 화면 확인 응답을 logs/prod-check.md에 기록
- AC-04(REQ-09): 문서 갱신, verifier 결과 리뷰에서 일치 확인
- 하네스 verify(기존 전체 검사) 통과

## 하지 않을 일

- 알림 규칙(사용자 결정으로 보류)
- perf 대시보드 변경
- 앱 코드·새 지표 추가
- 로컬 Prometheus 수집 수정(로컬은 관리 포트가 없어 401, prod-metrics-scrape에서 범위 제외)
- 대시보드 JSON 자동 검사를 checks.json에 상시 추가(대시보드 변경 빈도가 낮아 이번 작업의 일회성 검사 기록으로 둔다)

## 커밋 계획

1. `feat(infra)`: 운영 대시보드를 `J-Bank 운영`으로 바꾸고 입금 반영·API·JVM·오류율 패널 추가 (대시보드 JSON 1개)
2. `docs`: 인프라 문서 3.5절 대시보드 설명
3. `docs(devlog)`: 개발일지
4. `chore(harness)`: 작업 기록

병합 뒤 후속 PR(운영 확인):
5. `chore(harness)`: 운영 반영 확인 결과·완료 기록

각 커밋은 기존 빌드·테스트에 영향이 없다(대시보드 JSON·문서만 변경). 1의 병합은 backend-cd를 일으켜 api 재기동이 함께 일어난다.

## 적용 영역과 상세 기준

- 프론트: 해당 없음(앱 화면 변경 없음).
- 백엔드·데이터: 해당 없음(앱 코드·지표·DB 변경 없음).
- AI: 해당 없음.
- DevOps: 적용(관측 설정·배포 반영).
- 보안: 해당 없음(Grafana 접근 방식·권한·비밀값 변경 없음, 새 노출 경로 없음).
- 성능: 해당 없음(아래 성능 테스트 참고).
- 전체 흐름: 해당 없음(사용자 기능 변경 없음).

| 기준 ID | 완료 기준·시나리오 | 기대 결과·수치 목표 | 실행 명령 | 시점 | 증거 위치 |
| --- | --- | --- | --- | --- | --- |
| OPS-01 | AC-01: 저장소 파일만으로 프로비저닝 재현 | 로컬 Grafana 11.3.1에 폴더 마운트만으로 로드, 수동 설정 없음 | `docker run grafana/grafana:11.3.1` + 대시보드 API 조회 | 구현 후 | logs/dashboard-check.txt |
| OPS-03 | AC-01: 설정 검사 | JSON 파싱·id 고유·겹침 없음·datasource uid 일치, 기존 패널 불변 | 구조 검사 스크립트(명령은 로그에 기록) | 구현 후 | logs/dashboard-check.txt |
| OPS-07 | AC-02·03: 관측 경로가 실제 지표를 보여 줌. 알림 도착 확인은 사용자 결정(알림 보류)으로 제외. 의도한 오류는 안전한 GET 404(`/actuator/health`)·401(없는 업무 경로)로만 만들고, 기록에 실제 status·uri를 남긴다. 5xx는 운영에서 안전하게 만들 수 없어 값 0 확인까지만 함(한계로 기록) | REQ-07 기대값 충족, 배포 뒤 Grafana 프로비저닝 오류 0건 | SSM `wget localhost:9090/api/v1/query`, 외부 `curl /api/v1/products`, SSM Grafana 로그 grep | 병합 전·배포 후 | logs/prod-query.md, logs/prod-check.md |
| OPS-02 | 해당 없음: CI 구성 변경 없음(기존 backend-ci·frontend-ci 그대로) | - | - | - | - |
| OPS-04·05·06·08 | 해당 없음: 기동·배포 방식·리소스·백업 변경 없음. 대시보드는 Grafana 재시작 없이 30초 주기로 다시 읽힘 | - | - | - | - |
| SEC-01~08 | 해당 없음: 권한·외부 입력·비밀·의존성·노출 경로 변경 없음 | - | - | - | - |
| BE-01~08 | 해당 없음: 백엔드 변경 없음 | - | - | - | - |
| FE-01~07 | 해당 없음: 프론트 변경 없음 | - | - | - | - |
| E2E-01~06 | 해당 없음: 사용자 기능 변경 없음. 기존 회귀는 하네스 verify로 확인 | - | - | - | - |

## 일반 테스트 방법

| 완료 기준 | 시나리오·예상 결과 | 테스트 명령·근거 위치 | 실행 시점 |
| --- | --- | --- | --- |
| AC-01 | 구조 검사 통과, 로컬 Grafana 로드 성공(제목 `J-Bank 운영`, 패널 수 일치, 프로비저닝 오류 없음) | logs/dashboard-check.txt | 구현 후 |
| AC-02 | 운영 Prometheus 새 쿼리 success, 계열·라벨 기대대로 | logs/prod-query.md | 구현 후·병합 전 |
| AC-03 | 운영 Grafana 반영·오류 없음·사용자 화면 확인 | logs/prod-check.md | 배포 후 |
| AC-04 | 문서 일치 | verifier 결과 리뷰 | 구현 후 |
| 회귀 | 기존 전체 검사 | `python3 .claude/hooks/workflow.py verify` | 구현 후 |

## 성능 테스트

- 필요 여부와 이유: 불필요. 앱 요청 처리·DB 쿼리 변경이 없다. 대시보드를 열어 둘 때만 Grafana가 10초마다 Prometheus에 쿼리하고, 계열 수가 수십 개 수준이라 t3.small에서 부담이 작다.

## 추가 맥락

- 이전 작업: prod-metrics-scrape(ADR 0013)로 운영 Prometheus가 `api:9095`에서 수집을 시작했다(2026-10-02).
- 쿼리 원본: `infra/compose/perf/loadgen/provisioning/dashboards/json/jbank-perf.json` 패널 3·5·9·10·20·21·22.

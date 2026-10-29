# 작업 명세

## 요구사항 구체화

명세 상태: 확정
원문 요청: 문제가 생기면 알람이 (사용자 개인 메일) 여기로 오게 하고, 원래 한 곳에서 로그를 보고 딱 알 수 있어야하는거 아냐? 왤캐 로그가 서로 분산되어있지?
이어지는 답: 관측 스택 먼저 (교착 재현 실험은 이 작업 뒤 별도 작업)
요청 해석: 로컬과 운영에서 컨테이너 로그(api·postgres 포함)를 Grafana 한 곳에서 지표와 함께 조회하고, 교착·이체 서버 오류·수집 대상 다운·락 대기 급증이 생기면 사용자 메일로 알림과 해제 알림이 오게 한다. 후속 교착 재현 실험에서 "알림 → 로그로 원인 분석 → 해결" 흐름을 로컬에서 캡처하는 기반이 된다.
조사한 사실:
- 운영(`infra/compose/docker-compose.prod.yml`): caddy·api·postgres·redis·prometheus·grafana. 로그 수집기·postgres-exporter·알림 규칙 없음. Prometheus는 `api:9095`만 수집(`observability/prometheus.prod.yml`, 파일 하나만 마운트). api는 `env_file: .env`로 `.env` 전체(DB_PASSWORD·JWT_SECRET·PII_ENCRYPTION_KEY 등)를 받는다.
- 로컬(`infra/compose/docker-compose.yml`): 프로필 core(postgres·redis)·api(api 컨테이너)·observability(prometheus·grafana). Prometheus는 `host.docker.internal:8080/actuator/prometheus`를 수집하지만 로컬 api에 관리 포트(`MANAGEMENT_SERVER_PORT`)가 없어 `PrometheusScrapeRequestMatcher`가 허용하지 않고 401(ADR 0013). 지금 로컬 Prometheus는 api 지표를 못 가져온다. 로컬 api healthcheck는 `8080/actuator/health/readiness`.
- postgres-exporter는 부하 대상 구성(`infra/compose/perf/docker-compose.target.yml`)에만 있다. 교착 카운터(`pg_stat_database_deadlocks`)는 운영·로컬 어디서도 수집되지 않는다.
- 앱 지표: `http_server_requests_seconds_*`(uri·status), `db_lock_wait_seconds_*`, `hikaricp_*`가 대시보드(`jbank-transfer.json`)에 쓰인다. 이체 URI는 `/api/v1/transfers`, `/api/v1/transfers/{transactionId}/otp-verifications`. 미인증 요청은 보안 필터에서 끝나 이체 URI·락 지표가 남지 않는다.
- 운영 측정(원본은 progress.md 부록, start 뒤 `logs/prod-measure.md`): t3.small 1913MB, 부팅 배치 종료 후 유휴 available 944MB, swap 없음. api 502MiB·grafana 101MiB·postgres 63MiB·prometheus 44MiB·caddy 27MiB·redis 10MiB. 운영 compose v2.29.7. 부팅 직후 api 수집 실패 15~30초(10/26~10/29).
- `perf/results/ec2-baseline/bottleneck-analysis.md` 가설 E: 배치 JVM(`run-batch.sh`, `-Xmx512m`)이 뜨자 가용 807→127MB, 스래싱으로 50분 무응답. 재측정 S5 배치 구간 최저 204~241MB, 회차 최저 181~204MB에서는 정상. 운영 배치는 부팅 직후 돈다(ADR 0011).
- `ledgerReconciliationJob`(`LedgerReconciliationTasklet`)은 업무 데이터를 읽고 경고 로그만 남긴다(배치 메타데이터 기록 제외). 배치 메모리 측정에 수동 1회 실행해도 업무 데이터가 바뀌지 않는다.
- 배포 경로: `backend-cd.yml`의 paths에 `infra/compose/**`가 있어 병합하면 이미지 빌드·배포가 돈다. 인스턴스가 켜져 있으면 `deploy-ec2.sh`가 호스트 저장소를 origin/main으로 맞추고 `deploy.sh`(`up -d api`)를 실행한다. 꺼져 있으면 다음 부팅에 `sync-latest.sh`가 새 revision을 보고 같은 `deploy.sh`를 실행한다. `up -d api`는 `--no-deps`가 없어 설정이 바뀐 의존 서비스(postgres)도 재생성한다. 새 서비스(loki 등)는 의존 대상이 아니라 생기지 않는다. 기존 prometheus·grafana는 재시작 시 바인드 마운트된 새 설정 파일을 읽는다(중간 상태). 호스트에서만 되돌린 상태는 다음 배포·부팅에 덮인다(ADR 0011).
- `.gitignore`는 `.env*`를 무시하고 `.env.example`만 예외. 운영 compose는 `.env`에서 `COMPOSE_PROFILES`를 읽는다.
- Alertmanager 설정은 환경변수 치환을 지원하지 않고, email 수신자의 `to`·smarthost가 비면 설정 검증에 실패해 기동하지 않는다.
- Alloy의 도커 로그 수집은 컨테이너 목록·inspect·logs API를 쓴다. inspect 응답에는 컨테이너 환경변수가 평문으로 들어 있다.
질문하지 않은 이유: 동작·위험을 바꾸는 선택은 Q-01~Q-15로 물어 모두 답을 받았다. D-01~D-11은 사용자 결정이 아니라 답변을 구현하기 위한 메인의 선택이며 근거를 함께 적는다.

| 질문 ID | 결정 사항과 영향 | 사용자 답변·근거 | 상태 |
| --- | --- | --- | --- |
| Q-01 | 로그 저장 위치 | Loki+Alloy를 같은 서버(로컬·운영 compose)에 추가 | 해결 |
| Q-02 | 알림 종류 | 교착 발생(5분 내 카운터 1 이상 증가), 이체 API 5xx 비율(5분간 5% 이상, 4xx 제외), api 다운(수집 실패 2분 이상, Q-13으로 확장), 락 대기 급증(최대값 1초 초과) | 해결 |
| Q-03 | 적용 환경 | 로컬+운영 둘 다 | 해결 |
| Q-04 | 메일 발송 수단 | Gmail SMTP(앱 비밀번호를 사용자가 운영 서버에 직접 입력) | 해결 |
| Q-05 | 관측 컨테이너 메모리 부족 시 | 컨테이너별 상한. 넘으면 그 컨테이너만 재시작, 로그 일부 유실 허용 | 해결 |
| Q-06 | 로그 보관 기간 | 15일 | 해결 |
| Q-07 | 1초 넘는 락 대기 로그(`log_lock_waits`) | 로컬+운영 둘 다 켬 | 해결 |
| Q-08 | 알림 규칙 위치 | Prometheus 규칙 + Alertmanager, promtool 단위 테스트 | 해결 |
| Q-09 | 도커 소켓 권한 | 소켓 프록시로 컨테이너 조회·로그 읽기만 허용 | 해결 |
| Q-10 | 인스턴스 사양·유휴 메모리 기준 | t3.small 유지. 운영 반영 후 유휴 available 600MB 미만이면 운영 로그 수집 3종(loki·alloy·socket-proxy)을 내리고 알림만 유지 | 해결 |
| Q-11 | 병합 시점(병합하면 자동 배포가 운영 postgres를 재생성) | 18시 이후 서버가 꺼진 뒤 병합. 다음 날 부팅 때 사용자 없는 상태에서 재생성 | 해결 |
| Q-12 | 배치 실행 중 메모리 기준(사용자에게 127MB 사고·181~241MB 정상 사례를 알린 뒤 결정) | 반영 후 원장 대사 배치를 1회 수동 실행하며 최저 available 측정, 150MB 미만이면 운영 로그 수집 3종을 내림. Q-10과 함께 적용 | 해결 |
| Q-13 | exporter가 죽으면 교착 알림이 조용히 꺼짐 | 다운 알림을 api·postgres-exporter 수집 실패 2분 이상으로 넓힘. 알림 종류는 4개 유지 | 해결 |
| Q-14 | 수집기(Alloy)가 침해되면 inspect로 api 비밀값을 볼 수 있는 남는 위험 | 수용. 프록시·수집기 전용 네트워크와 경로 허용 목록으로 줄이고 ADR에 기록 | 해결 |
| Q-15 | exporter는 살아 있지만 DB 접속 실패(`pg_up 0`)면 교착 알림이 조용히 꺼짐 | 다운 알림에 추가: exporter의 `pg_up==0`이 2분 이상이면 같은 다운 알림(대상 postgres) 발생. 알림 종류는 4개 유지 | 해결 |

설계 결정(메인 선택):
- D-01 로컬 알림 메일은 Mailpit(observability 프로필)으로 받는다. 근거: 로컬 검증에 실제 Gmail 비밀번호가 필요 없고 API로 자동 확인된다. 실제 Gmail 도착은 배포 후 점검에서 사용자가 확인한다.
- D-02 메일 변수(SMTP 계정·앱 비밀번호·받는 주소)는 운영 `infra/compose/.env.alertmanager`(chmod 600, git 제외)에 두고 alertmanager만 `env_file`(`required: false`)로 받는다. 진입 스크립트가 기동 시 설정 파일을 만든다. 근거: 공개 저장소에 개인 메일·비밀번호를 남기지 않고 api에 Gmail 앱 비밀번호가 들어가지 않게 한다. 변수 이름은 `.env.example` 주석으로 안내한다.
- D-03 메일 변수가 하나라도 비었거나 파일이 없으면 진입 스크립트가 경고 로그를 남기고 메일 없는 수신자로 기동한다. 값이 틀리면 정상 기동하고 발송 때 실패 로그가 남는다. 근거: 비밀값 누락이 알림 조회나 다른 서비스 기동을 막지 않게 한다.
- D-04 로컬 api 컨테이너에 `MANAGEMENT_SERVER_PORT=9095`를 주고 로컬 Prometheus 대상을 `api:9095`로, healthcheck를 9095로 옮긴다. bootRun api는 수집·로그 대상이 아니다(지금도 401). 근거: 운영과 같은 경로(ADR 0013). 후속 교착 실험은 컨테이너 api로 한다.
- D-05 socket-proxy는 경로 허용 목록을 지원하는 프록시(wollomatic/socket-proxy)로 GET `/_ping`·`/version`·`/containers/json`·`/containers/{id}/json`·`/containers/{id}/logs`만 허용하고 나머지(archive·exec·모든 POST·DELETE)는 거부한다. socket-proxy와 alloy만 붙는 전용 내부 네트워크(`docker-api`, internal)를 두고, alloy는 loki에 닿도록 기본 네트워크에도 붙는다. 근거: Q-09·Q-14.
- D-06 메모리 상한: loki 192MB, alloy 96MB, postgres-exporter 32MB, alertmanager 32MB, socket-proxy 16MB, 합계 368MB, restart `unless-stopped`. 유휴 944MB에서 상한을 모두 써도 약 576MB가 남아 600MB 기준(Q-10)에 걸릴 수 있다. 실제 사용량은 가정하지 않고 배포 후 유휴(Q-10)·배치(Q-12) 측정으로 판정한다. 로컬 Mailpit은 운영에 없어 상한 대상이 아니다.
- D-07 postgres-exporter는 기존 superuser `jbank` 계정을 `DATA_SOURCE_URI`·`DATA_SOURCE_USER`·`DATA_SOURCE_PASS`(=`${DB_PASSWORD}`)로 받는다. 근거: 운영 DB 볼륨이 있어 전용 계정은 수동 SQL·비밀값이 더 필요하다. 외부 포트 없는 내부 전용이다. 위험은 수용으로 기록하고 pg_monitor 전용 계정은 후속 개선. URL 안에 비밀번호를 넣지 않아 base64 비밀번호의 `/`·`+`·`=`로 주소가 깨지지 않는다.
- D-08 Prometheus `rule_files`는 glob(`/etc/prometheus/rules/*.yml`)으로 지정한다. 근거: 재생성 전 기존 prometheus 컨테이너(규칙 디렉터리 미마운트)도 새 설정 파일로 정상 기동해야 한다. 없는 alertmanager·exporter 대상은 연결 실패만 남는다.
- D-09 Alertmanager 메일 주기: `group_wait 30s`, `group_interval 5m`, `repeat_interval 12h`, `send_resolved: true`.
- D-10 운영 loki·alloy·socket-proxy는 compose 프로필 `logs`에 두고 운영 `.env`의 `COMPOSE_PROFILES=logs`로 켠다. 근거: 600MB·150MB 기준 미달로 내릴 때 `.env`에서 빼면 이후 전체 `up -d`에도 다시 올라오지 않는다. alertmanager·postgres-exporter는 프로필 없이 항상 켠다. 로컬은 observability 프로필에 모두 둔다.
- D-11 alertmanager 상태는 볼륨(`alertmanager-data`)에 둔다. 근거: 재기동 뒤 같은 알림 메일을 다시 보내지 않게 한다.

## 목표

로컬과 운영에서 로그·지표·알림을 Grafana 한 곳에 모으고, 정해진 네 가지 문제 상황이 생기면 사용자 메일로 알림과 해제 알림이 오게 한다. 운영 메모리 여유를 측정 근거로 관리한다.

## 범위

- `infra/compose/docker-compose.yml`, `infra/compose/docker-compose.prod.yml`: loki·alloy·socket-proxy·postgres-exporter·alertmanager 추가(로컬은 mailpit 추가), postgres `log_lock_waits=on`, 메모리 상한, 전용 네트워크, 운영 `logs` 프로필, 로컬 api 관리 포트·healthcheck.
- `infra/compose/observability/`: Loki·Alloy·Alertmanager 설정과 진입 스크립트, Prometheus 수집 대상·알림 규칙, Grafana 데이터소스(Loki·Alertmanager), promtool 테스트.
- `infra/compose/.env.example`: `COMPOSE_PROFILES`, `.env.alertmanager` 변수 이름 안내.
- 문서: `docs/runbook/observability.md`(운영 반영·측정·내림·롤백), ADR 0014, `docs/06_J-Bank_인프라아키텍처.md` 관측 절, `docs/10_J-Bank_폴더구조.md`(서비스 목록, 로컬·운영 수집 경로 `api:9095`), 개발일지.

## 요구사항

| ID | 조건·입력 | 기대 동작·실패 조건 |
| --- | --- | --- |
| REQ-01 | 로컬(core·api·observability 프로필)과 운영 compose(`logs` 프로필) | Grafana의 Loki 데이터소스에서 api·postgres 컨테이너 로그를 컨테이너 이름 라벨로 골라 조회할 수 있다. 실패: 데이터소스 없음, api 또는 postgres 로그 0줄, 이름 대신 ID만 있음 |
| REQ-02 | 로그가 쌓인 상태 | 15일보다 오래된 로그는 compactor가 삭제한다(`retention_period: 360h`, `compactor.retention_enabled: true`, `delete_request_store` 설정). 실패: 하나라도 없음 |
| REQ-03 | postgres에서 교착·1초 넘는 락 대기 | `deadlock detected`와 `DETAIL`(서로 기다린 프로세스·쿼리), `still waiting for` 로그가 Loki에서 조회된다 |
| REQ-04 | 교착 발생 | Prometheus가 jbank DB 교착 카운터를 수집하고, 5분 창 증가량이 0보다 크면 교착 알림 발생. 증가 없으면 미발생 |
| REQ-05 | 이체 API(`/api/v1/transfers` 및 하위 경로) 요청 | 5분간 이체 요청 중 5xx 비율 5% 이상이면 알림, 미만이면 없음. 4xx는 분자에 넣지 않는다. 이체 요청 0건이면 없음. 규칙의 지표·라벨이 로컬 Prometheus에 실제로 있다 |
| REQ-06 | api 수집 실패, postgres-exporter 수집 실패, exporter의 DB 접속 실패(`pg_up==0`) | 어느 경우든 2분 이상 계속되면 수집 대상 다운 알림(대상 이름 포함), 2분 미만이면 없음. 모두 정상이면 `up==1`·`pg_up==1`이고 알림 inactive |
| REQ-07 | 계좌 락 대기 | 락 대기 최대값 1초 초과면 알림, 1초 이하면 없음. 규칙의 지표가 로컬 Prometheus에 실제로 있다 |
| REQ-08 | 알림 발생·해제 | 발생 메일과 해제 메일이 간다(알림 이름·요약 포함). 운영은 Gmail SMTP로 사용자 메일, 로컬은 Mailpit. 발생 중인 알림을 Grafana에서 조회할 수 있다. alertmanager 재기동 뒤 같은 알림을 다시 보내지 않는다 |
| REQ-09 | 비밀값 | 커밋 파일에 사용자 메일 주소·SMTP 비밀번호가 없다. api 컨테이너 환경변수에 메일 변수가 없다. 메일 변수가 비었거나 파일이 없으면 alertmanager는 경고 로그를 남기고 메일 없이 기동(재시작 반복 없음), 다른 서비스 정상. 값이 틀리면 기동하고 발송 실패가 로그에 남는다 |
| REQ-10 | 관측 컨테이너 메모리 | 상한(loki 192·alloy 96·exporter 32·alertmanager 32·socket-proxy 16MB)과 `unless-stopped` 설정. 로컬 e2e 동안 재시작 0회. 운영 반영 후 유휴 available 600MB 미만 또는 원장 대사 배치 1회 실행 중 최저 available 150MB 미만이면 운영에서 `logs` 프로필을 끄고 3종을 내리며 그 결과를 기록한다 |
| REQ-11 | 외부 노출·권한 | 새 컨테이너는 운영에서 호스트 포트가 없고 Caddy 프록시 대상이 아니다. 도커 소켓은 socket-proxy만 마운트한다. 프록시는 허용 목록 밖 요청(컨테이너 생성 POST, archive GET)을 거부한다. alloy 외 컨테이너(api·grafana)에서는 socket-proxy에 닿지 않는다 |
| REQ-12 | 병합 뒤 수동 반영 전 중간 상태 | 자동 배포 경로(`up -d api`)로 재생성되는 컨테이너는 {api, postgres}의 부분집합이고 새 서비스는 0개다. 기존 prometheus·grafana가 새 설정 파일로 재시작돼도 정상 기동한다(prometheus 준비 200, 기존 api 수집 유지) |

## 완료 기준

1. 설정 검사: 두 compose 파일 `config -q`(prod는 더미 `.env`, 특수문자 포함 비밀번호), `promtool check config`·`check rules`, `amtool check-config`(메일 있음·없음 두 경우의 생성 설정), `loki -verify-config`, `alloy fmt`가 모두 성공. 새 이미지는 모두 고정 버전 태그. (REQ-01~12 구성 근거)
2. promtool 단위 테스트: 교착 증가 0/1, 5xx 4.9%/5%·4xx만·요청 0, 다운 api·exporter 수집 실패·`pg_up==0` 각각 1분45초/2분, 락 대기 1초/1.01초가 모두 기대대로 통과. (REQ-04~07)
3. 로컬 통합 확인(core·api·observability 프로필):
   - 사전 조건: `up{job="jbank-api"}==1`, exporter `up==1`·`pg_up==1`, 모든 알림 inactive. (REQ-06)
   - 지표 존재: 로컬 api에 테스트 고객 가입·계좌 개설 후 인증된 성공 이체 1건(락 단계 통과) → `http_server_requests_seconds_count{uri=~"/api/v1/transfers.*"}`, `db_lock_wait_seconds_max`, `pg_stat_database_deadlocks{datname="jbank"}` 조회 결과가 비어 있지 않다. (REQ-04, 05, 07)
   - Loki API로 api·postgres 로그가 컨테이너 이름 라벨로 조회된다. (REQ-01)
   - psql 두 세션 교착 → `deadlock detected`·`DETAIL` 조회, 1초 넘는 락 대기 → `still waiting for` 조회. (REQ-03)
   - 같은 교착으로 카운터 증가 → 교착 알림 firing → firing 중 Grafana API(Alertmanager 데이터소스)로 알림 조회 → Mailpit에 발생 메일 → 해소 후 해제 메일. (REQ-04, 08)
   - api 2분 넘게 정지 → 다운 메일, 재기동 → 해제 메일. postgres-exporter 2분 넘게 정지 → 다운 메일. exporter의 DB 접속 정보를 틀리게 바꿔 `pg_up==0` 2분 넘김 → 다운 메일(대상 postgres), 원복 → 해제 메일. (REQ-06, 08)
   - 메일 대조: Mailpit API로 각 메일의 제목·본문에 상태(firing/resolved)·alertname·summary·다운 대상 이름이 있는지 확인. (REQ-08)
   - alertmanager 재기동 뒤 6분(`group_wait 30s` + `group_interval 5m` 이상) 동안 firing 중인 알림의 메일이 다시 오지 않는다. (REQ-08)
   - socket-proxy: alloy 네트워크에 붙인 curl 컨테이너(`--network container:<alloy>`)에서 POST `/containers/create`·GET `/containers/{id}/archive` 거부, api·grafana 컨테이너에서 `socket-proxy:2375` 접속 실패. (REQ-11)
   - 종료 시 새 컨테이너 RestartCount 0. (REQ-10)
4. 운영 구성 사전 검증(로컬에서 prod 파일 + 더미 `.env`, api 이미지는 로컬 빌드):
   - `COMPOSE_PROFILES=logs`로 기동 → Loki에서 api·postgres 로그 조회, `docker inspect`로 상한·restart·포트 없음·네트워크, Caddyfile 변경 없음. (REQ-01, 10, 11)
   - 내림 순서 재현: `logs`로 떠 있는 상태 → `.env`에서 `logs` 제거 → `docker compose stop loki alloy socket-proxy` → 전체 `up -d` → 3종이 계속 멈춰 있음. (REQ-10, D-10)
   - `.env.alertmanager` 없음 → 경고 로그 + 기동(재시작 0), 다른 서비스 healthy. 틀린 SMTP 값 → 기동 + 테스트 알림 발송 실패 로그. (REQ-09)
   - `docker inspect` api 환경변수에 메일 변수 0건. (REQ-09)
   - 중간 상태: 현재 main의 prod 구성으로 띄운 뒤 작업 브랜치 파일로 바꾸고 `up -d api` 실행 → 재생성된 컨테이너가 {api, postgres}의 부분집합이고 새 서비스 0개임을 기록. 이어서 전체 `up -d` 시 grafana가 재생성되지 않으면 재시작 후 Loki·Alertmanager 데이터소스가 조회되는지 확인. prometheus·grafana 재시작 → prometheus `/-/ready` 200, api 수집 유지, grafana 기동. (REQ-12)
5. 정적·비밀 확인: Loki 보관 키(REQ-02, 증거 `logs/config-check.md`), `git grep`으로 커밋 대상에 사용자 메일·비밀번호 0건, e2e 중 수집된 api·postgres 로그 표본에서 비밀번호·주민번호·JWT 형태 문자열 검색 결과 기록. (REQ-02, 09)
6. 앱 회귀: `workflow.py verify`(checks.json 전체) 통과. 앱 코드는 바꾸지 않는다.

배포 후 점검(작업 완료와 별도. 절차는 `docs/runbook/observability.md`):
- 18시 이후 서버가 꺼진 뒤 병합(Q-11). 다음 날 부팅 때 `sync-latest.sh`가 `deploy.sh`를 실행해 postgres가 재생성된다(배치 전, 사용자 없음).
- 반영 전 `free -m`·`docker stats --no-stream` 기록.
- 사용자가 `.env.alertmanager` 작성(chmod 600), `.env`에 `COMPOSE_PROFILES=logs` 추가 → `docker compose -f docker-compose.prod.yml up -d` → `docker compose restart grafana`(provisioning은 기동 때만 읽음) → Grafana에서 Loki·Alertmanager 데이터소스 조회 확인.
- 메모리 단위는 MiB(`MemAvailable` kB ÷ 1024). 5분 뒤 유휴 available 측정(기준 600MiB). 이어서 `run-batch.sh ledgerReconciliationJob` 1회 실행하며 1초 간격으로 MemAvailable 표본 → 최저값(기준 150MiB). 표본 중 150MiB 미만이 보이면 즉시 `docker compose stop loki alloy socket-proxy`. 무응답이 되면 EC2 중지·시작으로 복구(커밋된 데이터는 WAL로 복구). 둘 중 하나라도 미달이면 `.env`에서 `logs` 제거, 3종 정지 후 결과 기록(REQ-10).
- 테스트 알림을 Alertmanager에 넣어 사용자가 Gmail 발생·해제 메일 수신 확인, grafana.j-bank.site에서 운영 로그 조회(REQ-01, 08).
- 롤백: main에 revert 커밋을 18시 이후 병합 → 다음 부팅에서 sync-latest가 끝난 뒤(boot 로그 확인) 호스트에서 `up -d --remove-orphans`로 새 서비스 제거 → `docker compose restart grafana` → provisioning에서 빠진 Loki·Alertmanager 데이터소스를 Grafana에서 삭제. 호스트에서만 되돌리면 다음 배포·부팅에 덮인다.

## 하지 않을 일

- 교착 재현 실험과 포트폴리오 수정(후속 작업).
- 앱 코드 변경. 상관 ID 추적(OPS-07 일부)은 범위 제외, 알려진 한계로 기록.
- 부하 테스트 구성(`infra/compose/perf/`) 변경(기존 측정과 비교 조건 유지).
- 인스턴스 사양·Terraform·배포 스크립트(`deploy.sh`, backend-cd) 변경.
- postgres-exporter 전용 DB 계정(D-07, 후속 개선).
- 운영 배포 자체를 작업 완료 조건으로 삼지 않는다(배포 후 점검으로 분리).

## 적용 영역과 상세 기준

- 프론트: 해당 없음. 화면 변경 없음.
- 백엔드·데이터: 해당 없음. 앱 코드·스키마 변경 없음. `log_lock_waits`는 로그 출력만 바꾼다. postgres 재생성 시점은 Q-11로 정했다. 회귀는 verify.
- AI: 해당 없음.
- DevOps: 적용. 컨테이너·배포 구성·관측·알림 변경.
- 보안: 적용. SMTP 비밀·메일 주소·로그 개인정보·도커 소켓 권한.
- 성능: 앱 성능 측정은 해당 없음(요청 처리 경로·부하 구성 변경 없음). 자원 영향은 OPS-06 메모리로 다룬다.
- 전체 흐름: 해당 없음. 사용자 기능 변경 없음.

| 기준 ID | 완료 기준·시나리오 | 기대 결과·수치 목표 | 실행 명령 | 시점 | 증거 위치 |
| --- | --- | --- | --- | --- | --- |
| OPS-01 | 완료 기준 1 | 모든 검사 성공, 새 이미지 고정 태그 | `docker compose -f infra/compose/docker-compose.yml --profile core --profile api --profile observability config -q`, prod 파일 더미 .env로 같은 검사, promtool·amtool·loki·alloy를 각 이미지로 실행 | 구현 후 | `.claude/tasks/observability-logs-alerts/logs/config-check.md` |
| OPS-02 | 해당 없음 | CI 워크플로 변경 없음 | - | - | - |
| OPS-03 | 완료 기준 4의 메일 변수 없음·틀림, api 환경변수 | 없음: 경고+기동·재시작 0·타 서비스 정상. 틀림: 기동+발송 실패 로그. api 메일 변수 0건 | 로컬 prod 구성 기동, `docker logs`, `docker inspect` | 구현 후 | `logs/prod-preflight.md` |
| OPS-04 | 완료 기준 2·3의 수집 대상 다운 | 정상 시 `up==1`·inactive, 2분 미만 미발생, 2분 이상 발생. 운영 부팅 실패 구간 15~30초로 2분 기준 안 | promtool, 로컬 api·exporter 정지, `logs/prod-measure.md` | 구현 후 | `logs/promtool.md`, `logs/local-e2e.md`, `logs/prod-measure.md` |
| OPS-05 | 완료 기준 4의 중간 상태(`up -d api` 재생성 범위) + runbook의 반영·롤백 | 재생성 api·postgres뿐, prometheus 준비 200·수집 유지. 운영 반영·롤백은 배포 후 점검 | 로컬 prod 구성 전환 절차 | 구현 후 / 배포 후 | `logs/prod-preflight.md`, `docs/runbook/observability.md` |
| OPS-06 | 완료 기준 3·4의 상한·재시작·프로필 + 배포 후 유휴·배치 측정 | 상한 합계 368MB, 재시작 0, 프로필 끄면 3종 미기동. 운영 유휴 600MB 이상·배치 중 최저 150MB 이상이면 유지, 미달이면 3종 내림 | `docker inspect`, 운영 `free -m`·`docker stats`·MemAvailable 표본 | 구현 후 / 배포 후 | `logs/local-e2e.md`, `logs/prod-preflight.md`, 배포 후 기록 |
| OPS-07 | 완료 기준 2·3 | 교착·api·exporter 정지로 로그 조회·알림 발생/해제 메일·Grafana 알림 조회. 상관 ID는 범위 제외 | promtool, 로컬 통합 확인 | 구현 후 | `logs/promtool.md`, `logs/local-e2e.md` |
| OPS-08 | 해당 없음 | 백업·복원 변경 없음. Loki 로그는 15일 운영 로그로 복원 대상 아님 | - | - | - |
| SEC-01~04 | 해당 없음 | 인증·권한·입력·브라우저 경계 변경 없음 | - | - | - |
| SEC-05 | 완료 기준 5 | 커밋 대상 메일·비밀번호 0건. 수집 로그 표본(api, postgres DETAIL·STATEMENT) 검색 결과 기록. 운영 로그는 grafana.j-bank.site 관리자 로그인 뒤에서만 보인다는 접근 정책을 runbook·ADR에 기록 | `git grep`, Loki 조회 결과 검색 | 구현 후 | `logs/secret-check.md` |
| SEC-06 | 미검증(수용) | 새 이미지 고정 버전만 확인. 취약점 스캔은 하지 않으며 통과로 보지 않는다. 권한이 큰 socket-proxy·alloy가 대상임을 기록 | - | - | task.md, ADR |
| SEC-07 | 완료 기준 3·4: 소켓은 socket-proxy만 마운트, 허용 목록 밖 거부, 전용 네트워크로 타 컨테이너 접근 불가, 새 서비스 외부 포트 없음, 비밀 파일 분리. 남는 위험(alloy 침해 시 inspect로 api 환경변수 노출, exporter superuser)은 수용으로 ADR에 기록 | POST·archive 거부, api·grafana에서 프록시 접속 실패, api 메일 변수 0건, `ports` 없음 | compose 검사, `docker inspect`, 각 컨테이너에서 프록시 요청 | 구현 후 | `logs/config-check.md`, `logs/local-e2e.md` |
| SEC-08 | 해당 없음 | 요청 처리·남용 경로 변경 없음 | - | - | - |

## 일반 테스트 방법

| 완료 기준 | 시나리오·예상 결과 | 테스트 명령·근거 위치 | 실행 시점 |
| --- | --- | --- | --- |
| 1 | 설정 문법·참조 오류 없음 | OPS-01 명령 | 구현 후 |
| 2 | 규칙별 발생·미발생 경계 | `docker run --rm -v "$PWD/infra/compose/observability:/w" --entrypoint promtool prom/prometheus:v2.55.1 test rules /w/tests/alerts.test.yml` | 구현 후 |
| 3 | 로컬 사전 조건·지표 존재·로그·교착·락 대기·알림 메일·Grafana 알림·재기동 중복 없음·프록시 거부·재시작 0 | compose 기동 후 psql·api 요청·Prometheus·Loki·Grafana·Mailpit API 조회를 `logs/local-e2e.md`에 명령과 결과로 기록 | 구현 후 |
| 4 | 운영 구성 로그·상한·포트·네트워크·프로필·메일 변수 없음/틀림·api 환경변수·중간 상태 | 로컬 prod 구성 기동 절차를 `logs/prod-preflight.md`에 기록 | 구현 후 |
| 5 | 보관 설정·비밀 미포함·로그 표본 | 설정 파일, `git grep`, Loki 조회 | 구현 후 |
| 6 | 앱 회귀 | `python3 .claude/hooks/workflow.py verify` | 구현 후 |

정상·실패·경계는 완료 기준 2의 경계 사례와 3·4의 실패 주입으로 다룬다. 권한·동시 요청은 사용자 기능 변경이 없어 제외한다. 5xx·락 대기 알림의 실제 상황 재현은 인증된 이체 부하가 필요해 promtool로 판정하고, 지표 이름·라벨은 완료 기준 3에서 실제 조회로 확인한다. 메일 경로는 교착·다운 알림으로 확인한다(같은 Alertmanager 경로).

## 성능 테스트

- 필요 여부와 이유: 불필요. 앱 요청 처리 경로와 부하 테스트 구성을 바꾸지 않는다. 운영 메모리 영향은 OPS-06에서 상한·유휴 600MB·배치 중 150MB 기준으로 다룬다.

## 커밋 계획

1. `fix(infra): 로컬 Prometheus가 api 관리 포트로 지표를 수집` — 로컬 api `MANAGEMENT_SERVER_PORT`·healthcheck, 대상 `api:9095`
2. `feat(infra): postgres-exporter로 교착 카운터 수집` — 로컬·운영
3. `chore(infra): postgres 락 대기 로그 활성화`
4. `feat(infra): Loki·Alloy로 컨테이너 로그를 그라파나에 수집` — socket-proxy·전용 네트워크, 보관 15일, 상한, 운영 `logs` 프로필, Grafana 데이터소스
5. `feat(infra): Prometheus 알림 규칙과 Alertmanager 메일 발송 추가` — 규칙 4종, 진입 스크립트, 상태 볼륨, 로컬 Mailpit, `.env.example`, Grafana Alertmanager 데이터소스
6. `test(infra): 알림 규칙 promtool 단위 테스트 추가`
7. `docs: 관측 스택 ADR·운영 반영 절차 추가` — ADR 0014, runbook, 인프라 아키텍처·폴더 구조 문서
8. `docs(devlog): 관측 스택 개발일지`

각 커밋 뒤 compose `config -q`가 성공하는 상태를 유지한다.

## 추가 맥락

- 이 작업 전 대화: 포트폴리오의 교착 해결 항목에 "수정 전" 근거를 보강하려다, 운영에서 교착을 알아챌 수단(알림·로그 집중)이 없다는 점을 확인해 관측 스택을 먼저 하기로 함.
- 알려진 한계(ADR·runbook에 기록): 이체 트래픽이 거의 없어 5xx 한 건만으로 5%를 넘을 수 있음(오알림 많으면 최소 요청 수 조건 검토). 18:00 종료 때 발생 중이던 알림은 해제 메일이 오지 않음. EC2가 꺼진 동안은 알림 없음(ADR 0011, 의도된 동작). 상관 ID 없음.
- 인스턴스 상향(t3a.medium 등)은 검토 후 보류(Q-10). 기준 미달로 로그 수집을 내리게 되면 재검토 근거가 된다.

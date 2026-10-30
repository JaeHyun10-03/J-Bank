# 운영 구성 사전 검증 (완료 기준 4, REQ-01·09·10·11·12, OPS-03·05·06, SEC-07)

로컬 OrbStack에서 운영 compose를 저장소 밖 임시 폴더(프로젝트 이름 `prodcheck`)로 띄웠다. 더미 `.env`는 로컬 compose와 같은 로컬 전용 키, `DB_PASSWORD`에 `/ + = @`, `IMAGE_TAG=local`(로컬 빌드 api 이미지를 운영 이미지 이름으로 태그). 작업 브랜치 커밋 5419093, 비교 기준 origin/main 34ea684.
스크립트: 세션 스크래치 `preflight.sh`. 원본 출력 그대로:

```text
# 더미 .env: 로컬 compose와 같은 로컬 전용 키, DB_PASSWORD는 특수문자 포함. IMAGE_TAG=local

### 1. 현재 main(origin/main 34ea684)의 운영 구성으로 기동  [11:18:56Z]
$ dc up -d
 Container prodcheck-redis-1 Created 
 Container prodcheck-postgres-1 Created 
 Container prodcheck-api-1 Creating 
 Container prodcheck-api-1 Created 
 Container prodcheck-caddy-1 Creating 
 Container prodcheck-caddy-1 Created 
 Container prodcheck-grafana-1 Starting 
 Container prodcheck-postgres-1 Starting 
 Container prodcheck-redis-1 Starting 
 Container prodcheck-prometheus-1 Starting 
 Container prodcheck-prometheus-1 Started 
 Container prodcheck-grafana-1 Started 
 Container prodcheck-redis-1 Started 
 Container prodcheck-postgres-1 Started 
 Container prodcheck-postgres-1 Waiting 
 Container prodcheck-postgres-1 Healthy 
 Container prodcheck-api-1 Starting 
 Container prodcheck-api-1 Started 
 Container prodcheck-caddy-1 Starting 
 Container prodcheck-caddy-1 Started 
exit=0
api healthy
기존 서비스: api caddy grafana postgres prometheus redis 
api=c77d1d2d19cf
caddy=6887b47bb23a
grafana=70c8172185ee
postgres=83ebce8865f3
prometheus=2c20a914fc2f
redis=41a3b0956104

### 2. 작업 브랜치 파일로 교체 후 자동 배포 경로(deploy.sh와 같은 up -d api)  [11:19:13Z]
$ dc up -d api
 Container prodcheck-redis-1 Running 
 Container prodcheck-postgres-1 Recreate 
 Container prodcheck-postgres-1 Recreated 
 Container prodcheck-api-1 Running 
 Container prodcheck-postgres-1 Starting 
 Container prodcheck-postgres-1 Started 
 Container prodcheck-postgres-1 Waiting 
 Container prodcheck-postgres-1 Healthy 
exit=0
api healthy
api=c77d1d2d19cf
caddy=6887b47bb23a
grafana=70c8172185ee
postgres=51c3e335562a
prometheus=2c20a914fc2f
redis=41a3b0956104
재생성(ID 바뀜) 또는 새로 생긴 서비스:
  postgres 83ebce8865f3 -> 51c3e335562a
$ dc exec -T postgres psql -U jbank -d jbank -tAc show log_lock_waits
on
exit=0

### 3. 중간 상태: 기존 prometheus·grafana만 재시작(새 설정 파일, 규칙 디렉터리 미마운트)  [11:19:20Z]
$ dc restart prometheus grafana
 Container prodcheck-grafana-1 Restarting 
 Container prodcheck-prometheus-1 Restarting 
 Container prodcheck-prometheus-1 Started 
 Container prodcheck-grafana-1 Started 
exit=0
$ dc exec -T prometheus wget -q -S -O /dev/null http://localhost:9090/-/ready
  HTTP/1.1 200 OK
  Date: Thu, 29 Oct 2026 11:19:41 GMT
  Content-Length: 28
  Content-Type: text/plain; charset=utf-8
  Connection: close
  
exit=0
up: {"status":"success","data":{"resultType":"vector","result":[{"metric":{"__name__":"up","instance":"api:9095","job":"jbank-api"},"value":[1793272781.303,"1"]},{"metric":{"__name__":"up","instance":"postgres-exporter:9187","job":"postgres"},"value":[1793272781.303,"0"]}]}}
$ dc ps --format {{.Service}} {{.Status}}
api Up 38 seconds (healthy)
caddy Up 38 seconds
grafana Up 20 seconds
postgres Up 26 seconds (healthy)
prometheus Up 20 seconds
redis Up 44 seconds
exit=0

### 4. 전체 반영(COMPOSE_PROFILES=logs, .env.alertmanager 없음)  [11:19:41Z]
$ dc up -d
 Container prodcheck-alloy-1 Created 
 Container prodcheck-prometheus-1 Recreated 
 Container prodcheck-api-1 Recreated 
 Container prodcheck-caddy-1 Running 
 Container prodcheck-socket-proxy-1 Starting 
 Container prodcheck-postgres-1 Waiting 
 Container prodcheck-alertmanager-1 Starting 
 Container prodcheck-prometheus-1 Starting 
 Container prodcheck-postgres-exporter-1 Starting 
 Container prodcheck-loki-1 Starting 
 Container prodcheck-postgres-exporter-1 Started 
 Container prodcheck-socket-proxy-1 Started 
 Container prodcheck-loki-1 Started 
 Container prodcheck-alloy-1 Starting 
 Container prodcheck-alertmanager-1 Started 
 Container prodcheck-prometheus-1 Started 
 Container prodcheck-postgres-1 Healthy 
 Container prodcheck-api-1 Starting 
 Container prodcheck-alloy-1 Started 
 Container prodcheck-api-1 Started 
exit=0
$ dc restart grafana
 Container prodcheck-grafana-1 Restarting 
 Container prodcheck-grafana-1 Started 
exit=0
$ dc ps --format {{.Service}} {{.Status}}
alertmanager Up About a minute
alloy Up About a minute
api Up About a minute (healthy)
caddy Up About a minute
grafana Up About a minute
loki Up About a minute
postgres Up About a minute (healthy)
postgres-exporter Up About a minute
prometheus Up About a minute
redis Up About a minute
socket-proxy Up About a minute
exit=0
$ dc logs alertmanager
alertmanager-1  | alertmanager-entrypoint: 경고 — ALERT_EMAIL_TO·SMTP_SMARTHOST·SMTP_FROM 중 비어 있는 값이 있어 메일 없이 기동합니다
alertmanager-1  | time=2026-10-29T11:19:42.993Z level=INFO source=app.go:194 msg="Starting Alertmanager" version="(version=0.34.1, branch=HEAD, revision=73c6bfe7393929211294c1954f30d8ed78e4d0ad)"
alertmanager-1  | time=2026-10-29T11:19:42.994Z level=INFO source=app.go:196 msg="Build context" build_context="(go=go1.26.8, platform=linux/arm64, user=root@d3b71085e163, date=20260917-07:45:14, tags=netgo)"
alertmanager-1  | time=2026-10-29T11:19:42.998Z level=INFO source=cluster.go:192 msg="setting advertise address explicitly" component=cluster addr=172.23.0.8 port=9094
alertmanager-1  | time=2026-10-29T11:19:43.018Z level=INFO source=cluster.go:682 msg="Waiting for gossip to settle..." component=cluster interval=2s
alertmanager-1  | time=2026-10-29T11:19:43.060Z level=INFO source=tls_config.go:372 msg="Listening on" address=[::]:9093
alertmanager-1  | time=2026-10-29T11:19:43.060Z level=INFO source=tls_config.go:375 msg="TLS is disabled." http2=false address=[::]:9093
alertmanager-1  | time=2026-10-29T11:19:45.020Z level=INFO source=cluster.go:707 msg="gossip not settled" component=cluster polls=0 before=0 now=1 elapsed=2.001800543s
alertmanager-1  | time=2026-10-29T11:19:53.027Z level=INFO source=cluster.go:699 msg="gossip settled; proceeding" component=cluster elapsed=10.008461338s
exit=0
datasources: [('Alertmanager', 'alertmanager'), ('Loki', 'loki'), ('Prometheus', 'prometheus')]
health loki: {"message":"Data source successfully connected.","status":"OK"}
wget: server returned error: HTTP/1.1 500 Internal Server Error
health alertmanager: 
health prometheus: {"details":{"application":"Prometheus","features":{"rulerApiEnabled":false}},"message":"Successfully queried the Prometheus API.","status":"OK"}
loki containers: {"status":"success","data":["prodcheck-alertmanager-1","prodcheck-alloy-1","prodcheck-api-1","prodcheck-caddy-1","prodcheck-grafana-1","prodcheck-loki-1","prodcheck-postgres-1","prodcheck-postgres-exporter-1","prodcheck-prometheus-1","prodcheck-redis-1","prodcheck-socket-proxy-1"]}
loki api lines: 5
loki postgres lines: 5
up: {"status":"success","data":{"resultType":"vector","result":[{"metric":{"__name__":"up","instance":"api:9095","job":"jbank-api"},"value":[1793272855.517,"1"]},{"metric":{"__name__":"up","instance":"postgres-exporter:9187","job":"postgres"},"value":[1793272855.517,"1"]}]}}
pg_up: {"status":"success","data":{"resultType":"vector","result":[{"metric":{"__name__":"pg_up","instance":"postgres-exporter:9187","job":"postgres"},"value":[1793272855.616,"1"]}]}}
inspect (서비스 메모리MiB 재시작정책 재시작횟수 호스트포트 네트워크):
loki 192 unless-stopped 0 ports={} nets=prodcheck_default
alloy 96 unless-stopped 0 ports={} nets=prodcheck_default prodcheck_docker-api
socket-proxy 16 unless-stopped 0 ports={} nets=prodcheck_docker-api
postgres-exporter 32 unless-stopped 0 ports={} nets=prodcheck_default
alertmanager 32 unless-stopped 0 ports={} nets=prodcheck_default
api 환경변수 중 메일 변수: 0 건
alertmanager 환경변수 중 메일 변수: 0 건

### 5. 틀린 SMTP 값(.env.alertmanager)으로 기동 → 테스트 알림 발송 실패 로그  [11:20:55Z]
$ dc up -d alertmanager
 Container prodcheck-alertmanager-1 Recreate 
 Container prodcheck-alertmanager-1 Recreated 
 Container prodcheck-alertmanager-1 Starting 
 Container prodcheck-alertmanager-1 Started 
exit=0
api 환경변수 중 메일 변수: 0 건
alertmanager 환경변수 중 메일 변수: 5 건
$ dc exec -T alertmanager amtool alert add RunbookTest target=manual --annotation=summary=관측 스택 메일 확인 --end=2026-10-29T11:23:06Z --alertmanager.url=http://localhost:9093
time=2026-10-29T11:21:07.169Z level=WARN source=parse.go:136 msg="Alertmanager is moving to a new parser for labels and matchers, and this input is incompatible. Alertmanager has instead parsed the input using the classic matchers parser as a fallback. To make this input compatible with the UTF-8 matchers parser please make sure all regular expressions and values are double-quoted and backslashes are escaped. If you are still seeing this message please open an issue." input="summary=관측 스택 메일 확인" origin=cli err="11:13: unexpected 스택: expected a comma or close brace" suggestion="summary=\"관측 스택 메일 확인\""
exit=0
$ dc exec -T alertmanager amtool alert query --alertmanager.url=http://localhost:9093
Alertname    Starts At                Summary      State   
RunbookTest  2026-10-29 11:23:06 UTC  관측 스택 메일 확인  active  
exit=0
$ dc logs --since 70s alertmanager
alertmanager-1  | time=2026-10-29T11:20:56.703Z level=INFO source=app.go:194 msg="Starting Alertmanager" version="(version=0.34.1, branch=HEAD, revision=73c6bfe7393929211294c1954f30d8ed78e4d0ad)"
alertmanager-1  | time=2026-10-29T11:20:56.704Z level=INFO source=app.go:196 msg="Build context" build_context="(go=go1.26.8, platform=linux/arm64, user=root@d3b71085e163, date=20260917-07:45:14, tags=netgo)"
alertmanager-1  | time=2026-10-29T11:20:56.705Z level=INFO source=cluster.go:192 msg="setting advertise address explicitly" component=cluster addr=172.23.0.8 port=9094
alertmanager-1  | time=2026-10-29T11:20:56.725Z level=INFO source=cluster.go:682 msg="Waiting for gossip to settle..." component=cluster interval=2s
alertmanager-1  | time=2026-10-29T11:20:56.767Z level=INFO source=tls_config.go:372 msg="Listening on" address=[::]:9093
alertmanager-1  | time=2026-10-29T11:20:56.767Z level=INFO source=tls_config.go:375 msg="TLS is disabled." http2=false address=[::]:9093
alertmanager-1  | time=2026-10-29T11:20:58.728Z level=INFO source=cluster.go:707 msg="gossip not settled" component=cluster polls=0 before=0 now=1 elapsed=2.002402001s
alertmanager-1  | time=2026-10-29T11:21:06.744Z level=INFO source=cluster.go:699 msg="gossip settled; proceeding" component=cluster elapsed=10.01805563s
alertmanager-1  | time=2026-10-29T11:21:37.263Z level=WARN source=retry_stage.go:165 msg="Notify attempt failed, will retry later" component=dispatcher receiver=mail integration=email[0] aggrGroup="{}:{alertname=\"RunbookTest\", target=\"manual\"}" attempts=1 err="establish connection to server: dial tcp: lookup smtp.invalid on 127.0.0.11:53: no such host"
exit=0
alertmanager RestartCount=0 State=running

### 6. 로그 수집 끄기 순서 재현  [11:21:52Z]
$ dc stop loki alloy socket-proxy
 Container prodcheck-alloy-1 Stopping 
 Container prodcheck-alloy-1 Stopped 
 Container prodcheck-loki-1 Stopping 
 Container prodcheck-socket-proxy-1 Stopping 
 Container prodcheck-socket-proxy-1 Stopped 
 Container prodcheck-loki-1 Stopped 
exit=0
$ dc up -d
 Container prodcheck-grafana-1 Running 
 Container prodcheck-postgres-1 Running 
 Container prodcheck-alertmanager-1 Running 
 Container prodcheck-postgres-exporter-1 Running 
 Container prodcheck-redis-1 Running 
 Container prodcheck-prometheus-1 Running 
 Container prodcheck-api-1 Recreate 
 Container prodcheck-api-1 Recreated 
 Container prodcheck-caddy-1 Running 
 Container prodcheck-postgres-1 Waiting 
 Container prodcheck-postgres-1 Healthy 
 Container prodcheck-api-1 Starting 
 Container prodcheck-api-1 Started 
exit=0
  loki exited
  alloy exited
  socket-proxy exited
$ dc ps --format {{.Service}} {{.Status}}
alertmanager Up About a minute
api Up 5 seconds (health: starting)
caddy Up 2 minutes
grafana Up 2 minutes
postgres Up 2 minutes (healthy)
postgres-exporter Up 2 minutes
prometheus Up 2 minutes
redis Up 3 minutes
exit=0

### 7. 정리  [11:22:01Z]
$ dc down -v
 Container prodcheck-postgres-1 Stopped 
 Container prodcheck-postgres-1 Removing 
 Container prodcheck-redis-1 Stopped 
 Container prodcheck-redis-1 Removing 
 Container prodcheck-postgres-1 Removed 
 Container prodcheck-redis-1 Removed 
 Volume prodcheck_postgres-data Removing 
 Volume prodcheck_alertmanager-data Removing 
 Volume prodcheck_caddy-config Removing 
 Network prodcheck_default Removing 
 Volume prodcheck_caddy-data Removing 
 Volume prodcheck_grafana-data Removing 
 Volume prodcheck_prometheus-data Removing 
 Volume prodcheck_caddy-data Removed 
 Volume prodcheck_prometheus-data Removed 
 Volume prodcheck_grafana-data Removed 
 Volume prodcheck_postgres-data Removed 
 Volume prodcheck_caddy-config Removed 
 Volume prodcheck_alertmanager-data Removed 
 Network prodcheck_default Removed 
exit=0
script exit=0
```

## 판정

| 항목 | 결과 | 근거(위 출력) |
| --- | --- | --- |
| 중간 상태: 자동 배포 경로 `up -d api`의 재생성 범위 | 통과. 재생성은 postgres 하나({api, postgres}의 부분집합), 새 서비스 0개. `log_lock_waits=on` | 2단계 |
| 중간 상태: 기존 prometheus·grafana가 새 설정으로 재시작 | 통과. prometheus `/-/ready` 200, api `up=1` 유지(없는 exporter 대상은 0), grafana 기동 | 3단계 |
| 전체 반영(logs 프로필, `.env.alertmanager` 없음) | 통과. 11개 서비스 기동, alertmanager 경고 로그 + 메일 없이 기동, 재시작 0 | 4단계 |
| Grafana 데이터소스 | Prometheus·Loki health OK. Alertmanager health는 500 "Plugin unavailable"(Grafana가 이 유형의 상태 확인을 지원하지 않음). 같은 데이터소스 경유 조회는 로컬 e2e 4단계와 아래 추가 확인에서 성공 | 4단계 |
| Loki api·postgres 로그 | 통과(컨테이너 이름 라벨, 각 5줄 이상) | 4단계 |
| 상한·restart·포트·네트워크 | 통과. loki 192·alloy 96·socket-proxy 16·exporter 32·alertmanager 32MiB, unless-stopped, 재시작 0, 호스트 포트 없음, socket-proxy는 docker-api 네트워크만 | 4단계 |
| api 환경변수 메일 변수 | 0건(`.env.alertmanager` 있을 때도 0건, alertmanager만 5건) | 4·5단계 |
| 틀린 SMTP 값 | 통과. alertmanager 기동·재시작 0, 테스트 알림 발송 실패 로그(`Notify attempt failed ... no such host`) | 5단계 |
| 로그 수집 끄기 순서 | 통과. `.env`에서 logs 제거 → stop 3종 → `up -d` 뒤에도 loki·alloy·socket-proxy exited | 6단계 |
| Caddyfile 변경 없음 | 통과(`git diff --stat origin/main -- infra/compose/Caddyfile` 빈 출력, config-check.md) | - |

## 실행 중 발견해 runbook에 반영한 것

- `.env`에 `COMPOSE_PROFILES`를 넣거나 빼면 api도 재생성된다(4·6단계 `api Recreated`). api가 `env_file: .env`로 파일 전체를 받기 때문이다. runbook 2-3, 4-3에 "api 수 초 중단"을 적었다.
- Alertmanager 데이터소스 health가 지원되지 않아 runbook 2-5의 확인 방법을 Alerting 화면으로 바꿨다.
- `amtool alert add`의 주석 값에 공백이 있으면 파서 경고가 난다(5단계). 값을 `'summary="..."'`로 감싸도록 runbook 5-1을 고쳤다.

## 추가 확인 (runbook 명령, 로컬 compose)

```text
$ docker compose --profile observability exec -T alertmanager amtool alert add RunbookTest target=manual --annotation='summary="관측 스택 메일 확인"' --end="<+2분>" --alertmanager.url=http://localhost:9093
exit=0   (파서 경고 없음)
40초 뒤 Mailpit: ['[FIRING:1] RunbookTest manual']

$ curl -u admin:<로컬> localhost:3001/api/datasources/uid/alertmanager/health
{"message":"Plugin unavailable","messageId":"plugin.unavailable","statusCode":500,"traceID":""}
$ curl -u admin:<로컬> localhost:3001/api/datasources/proxy/uid/alertmanager/api/v2/status   → 200
$ curl -u admin:<로컬> localhost:3001/api/alertmanager/alertmanager/api/v2/alerts            → []
```

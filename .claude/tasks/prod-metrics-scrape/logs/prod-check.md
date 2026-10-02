# 운영 반영 확인 (AC-06·REQ-09·OPS-04·OPS-07·E2E-02·SEC-05·SEC-07)

- 대상: 운영 EC2 i-0b71df18394cf8010, 2026-10-02 09:00 KST 부팅(LaunchTime 2026-10-02T00:00:43Z)
- 배포 경로: PR #10 병합(81c5f57, 2026-10-01 20:52 KST) 때 인스턴스 stopped라 backend-cd가 배포를 건너뜀. 부팅 시 jbank-boot.service의 sync-latest가 반영(00:00:59~00:03:49Z 정상 종료).
- 방법: SSM Run Command(AWS-RunShellScript). 조회는 읽기 전용. 변경은 사용자 승인 후 `docker compose restart prometheus` 1회뿐. `.env`는 `IMAGE_TAG` 줄만, 컨테이너 env는 `MANAGEMENT_SERVER_PORT`만 출력해 비밀값을 남기지 않음.

## 1. 반영 상태 (SSM c25dd024-ac81-4dda-ae8b-f833582707ea, 09:18 KST)

| 항목 | 결과 |
| --- | --- |
| 운영 저장소 HEAD | `81c5f57 Merge pull request #10 from JaeHyun10-03/fix/prod-metrics-scrape` |
| `.env` IMAGE_TAG | `81c5f572a88b43b74b4663ce1acc346af4c39f6a` |
| api 이미지 revision 라벨 | `81c5f572a88b43b74b4663ce1acc346af4c39f6a` |
| api 컨테이너 | `Up 16 minutes (healthy)` (OPS-04: healthcheck가 관리 포트 readiness) |
| api env | `MANAGEMENT_SERVER_PORT=9095` |
| 컨테이너 안 `localhost:9095/actuator/health/readiness` | `{"status":"UP"}` |
| 컨테이너 안 `localhost:8080/readyz` | `{"status":"UP"}` |
| 컨테이너 안 `localhost:9095/actuator/prometheus`의 `jvm_` 줄 수 | 56 |
| api 로그 `Using generated security password` 건수 | 0 (SEC-05) |
| Prometheus target(재시작 전) | `api:8080`, health=down, lastError `server returned HTTP status 401` |

재시작 전 Prometheus는 부팅 직후 sync-latest의 git reset보다 먼저 떠서 옛 설정 파일(단일 파일 바인드 마운트)을 읽고 있었다. 계획(task.md 재점검 항목)에서 예상한 그대로다.

## 2. Prometheus 재시작 후 (SSM dda7e5bf-c897-4858-a5c7-bc2c8f75ae32, 사용자 승인, 재시작 45초 뒤)

| 항목 | 결과 |
| --- | --- |
| target | `api:9095`, health=up, lastError 없음, lastScrapeDuration 0.016s (OPS-07) |
| `up{job="jbank-api"}` | `instance="api:9095"` → 1. `instance="api:8080"` → 0은 재시작 전 마지막 값(조회 기본 5분 범위 안의 옛 시계열) |
| `jbank_credit_pending_count` | `instance="api:9095"` → 0 (결과 있음) |
| `hikaricp_connections_max` | 10 (Hikari 지표 수집 확인) |

## 3. 외부 확인 (로컬 curl, 09:20 KST)

| 요청 | 결과 | 기대 |
| --- | --- | --- |
| 미인증 `https://api.j-bank.site/actuator/health` | 404 `COMMON_004_NOT_FOUND` | 404 (변경 전 200, 본 포트에서 actuator가 사라진 증거) |
| 미인증 `https://api.j-bank.site/actuator/prometheus` | 401 `COMMON_002_UNAUTHORIZED` | 200 아님 (SEC-07) |
| `https://api.j-bank.site/readyz` | 200 `{"status":"UP"}` | 200 |
| `https://api.j-bank.site/livez` | 200 `{"status":"UP"}` | 200 |
| 운영 프론트 `https://www.j-bank.site/api/server-status` | 200 `{"online":true}` | online (E2E-02) |

## 4. 프론트 배포 커밋 (결과 리뷰 2회차 권고-2)

- `gh api repos/JaeHyun10-03/J-Bank/deployments`: Production `81c5f57`, 상태 success(vercel[bot], 2026-10-01T11:53:51Z). 운영 프론트가 `/readyz`를 부르는 새 코드다.

## 실행 명령 (재현용)

SSM 1차(읽기 전용, `/opt/jbank/infra/compose`에서 `C="docker compose -f docker-compose.prod.yml"`):

```bash
git -c safe.directory='*' -C /opt/jbank log -1 --format='%h %s'
grep '^IMAGE_TAG=' .env
$C ps --format '{{.Service}} | {{.Status}} | {{.Health}}'
docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' $($C ps -q api) | grep '^MANAGEMENT_SERVER_PORT='
docker inspect -f '{{index .Config.Labels "org.opencontainers.image.revision"}}' $($C ps -q api)
journalctl -u jbank-boot.service --since today --no-pager | tail -25
$C exec -T api wget -qO- http://localhost:9095/actuator/health/readiness
$C exec -T api wget -qO- http://localhost:8080/readyz
$C exec -T api sh -c 'wget -qO- http://localhost:9095/actuator/prometheus | grep -c "^jvm_"'
$C logs api 2>&1 | grep -c "Using generated security password"
$C exec -T prometheus wget -qO- http://localhost:9090/api/v1/targets
```

SSM 2차(사용자 승인):

```bash
$C restart prometheus && sleep 45
$C exec -T prometheus wget -qO- http://localhost:9090/api/v1/targets
$C exec -T prometheus wget -qO- 'http://localhost:9090/api/v1/query?query=up%7Bjob%3D%22jbank-api%22%7D'
$C exec -T prometheus wget -qO- 'http://localhost:9090/api/v1/query?query=jbank_credit_pending_count'
$C exec -T prometheus wget -qO- 'http://localhost:9090/api/v1/query?query=hikaricp_connections_max'
```

외부(로컬, 쿠키 없이):

```bash
curl -s -w '%{http_code}' https://api.j-bank.site/actuator/health   # /actuator/prometheus, /readyz, /livez 동일
curl -s -w '%{http_code}' https://www.j-bank.site/api/server-status
```

## 판정

REQ-09 기대 동작 모두 충족: target up, `up`=1, `jbank_credit_pending_count` 결과 있음, 외부 `/actuator/health` 404, `/actuator/prometheus` 비200, `/readyz` 200, 생성 비밀번호 로그 0건.

# ADR 0014: 로그를 Loki로 모으고 Prometheus 알림을 메일로 보낸다

## 상태

승인됨. 2026-10-29.

## 배경

운영에서 문제가 생겨도 알아챌 수단이 없었다. 지표는 Prometheus·Grafana에 있었지만 알림 규칙이 없고, 로그는 컨테이너마다 `docker logs`에 흩어져 있었다.
v2.0(ADR 0010)에서 "인스턴스 한 대의 로그는 `docker logs`로 충분하다"며 Loki를 뺐는데, 교착 같은 문제를 확인하려면 지표 그래프와 같은 시각의 DB 로그(`deadlock detected`의 `DETAIL`)를 한 화면에서 봐야 한다.

조사하면서 다음도 확인했다.

- 교착 카운터(`pg_stat_database_deadlocks`)는 운영·로컬 어디서도 수집되지 않았다. postgres-exporter는 부하 테스트 구성에만 있었다.
- 로컬 Prometheus는 관리 포트가 없는 api의 8080을 긁어 401로 실패하고 있었다(ADR 0013).
- 운영은 t3.small(2GB) 한 대이고, 부팅 배치 종료 후 유휴 가용 메모리가 944MiB였다(2026-10-29 측정). 과거 부하 + 배치 JVM에서 가용 127MB까지 떨어져 50분 무응답한 사고가 있다(`perf/results/ec2-baseline/bottleneck-analysis.md` 가설 E).

## 결정

- 로그: alloy가 컨테이너 로그를 읽어 loki(단일 바이너리, 파일시스템)로 보내고 Grafana의 Loki 데이터소스로 조회한다. 라벨은 `container`(compose-api-1 등)와 `service`(api 등). 보관 15일(`retention_period` + compactor `retention_enabled`).
- 도커 API: alloy는 socket-proxy를 거쳐서만 도커에 닿는다. 프록시는 GET `/_ping`·`/version`·`/networks`·`/containers/json`·`/containers/{id}/json`·`/containers/{id}/logs`와 HEAD `/_ping`만 허용하고 나머지(모든 쓰기, archive, exec)는 403으로 거부한다. 프록시는 alloy만 붙는 internal 네트워크(`docker-api`)에 있고 `-allowfrom=alloy`로 출발지도 제한한다.
- 지표: postgres-exporter를 추가해 교착 카운터와 DB 접속 상태(`pg_up`)를 수집한다. 로컬 api는 운영처럼 관리 포트 9095로 수집한다.
- DB 로그: `log_lock_waits=on`으로 1초(`deadlock_timeout`) 넘는 락 대기를 남긴다.
- 알림: Prometheus 규칙 4종(`observability/rules/alerts.yml`)을 Alertmanager가 메일로 보낸다. 발생·해제 모두 보낸다(`send_resolved`). 주기는 `group_wait 30s`, `group_interval 5m`, `repeat_interval 12h`.

  | 알림 | 조건 |
  | --- | --- |
  | DeadlockDetected | 5분 창 교착 카운터 증가 > 0 |
  | TransferServerErrorRate | 5분간 이체 API 5xx 비율 ≥ 5%(4xx 제외, 요청 0건이면 없음) |
  | TargetDown | api 수집 실패, exporter 수집 실패, exporter의 DB 접속 실패(`pg_up 0`)가 2분 이상. `target` 라벨로 구분 |
  | AccountLockWaitHigh | 계좌 락 대기 최대값 > 1초 |

- 메일: 운영은 Gmail SMTP. 받는 주소·SMTP 계정·앱 비밀번호는 `infra/compose/.env.alertmanager`(chmod 600, git 제외)에만 두고 alertmanager만 읽는다. Alertmanager 설정은 환경변수 치환을 못 해서 진입 스크립트가 기동 때 만든다. 값이 하나라도 비면 경고를 남기고 메일 없이 기동한다. 로컬은 mailpit이 받는다.
- 메모리: 상한 loki 192MB, alloy 96MB, postgres-exporter 32MB, alertmanager 32MB, socket-proxy 16MB(합 368MB). 운영의 loki·alloy·socket-proxy는 compose 프로필 `logs`로 두고 `.env`의 `COMPOSE_PROFILES=logs`로 켠다. 반영 후 유휴 가용 600MiB 미만이거나 원장 대사 배치 1회 중 최저 150MiB 미만이면 운영에서 이 세 서비스를 끈다(알림은 유지).
- 인스턴스 사양은 올리지 않는다(t3.small 유지). 위 기준에 걸려 로그 수집을 끄게 되면 사양 상향을 다시 검토한다.

## 검토한 대안

- **AWS CloudWatch Logs**: 운영 메모리를 더 쓰지 않지만 IAM·Terraform 변경과 비용이 들고, 로컬은 다른 방식이 되어 교착 재현 실험 구성이 운영과 달라진다.
- **Grafana Cloud**: 저장·알림을 외부에 맡기지만 외부 계정이 필요하고 로그가 외부로 나간다.
- **t3.medium(또는 t3a.medium)으로 상향**: 가장 안전하지만 인스턴스 비용이 약 2배다. 측정 기준을 두고 t3.small로 먼저 간다.
- **Grafana 내장 알림**: 컨테이너가 늘지 않지만 기준값을 자동 테스트할 도구가 없다. Prometheus 규칙은 promtool로 경계 사례를 검증한다(`observability/tests/alerts.test.yml`).
- **도커 소켓을 alloy에 직접 마운트**: 소켓 접근은 사실상 호스트 root다. `:ro` 마운트도 API 호출을 막지 못한다.
- **도커 로그 파일 직접 읽기**: 소켓이 필요 없지만 로그에 컨테이너 이름 대신 ID만 붙는다.

## 결과와 한계

- Grafana 한 곳에서 지표·로그·발생 중인 알림을 함께 본다. 운영 로그는 `grafana.j-bank.site` 관리자 로그인 뒤에서만 보인다.
- 남는 위험(수용):
  - inspect 응답에는 컨테이너 환경변수가 들어 있어, alloy가 침해되면 api의 비밀값(DB 비밀번호·암호화 키)을 읽을 수 있다. alloy는 외부 포트가 없고 loki에만 보낸다.
  - postgres-exporter는 superuser `jbank`로 접속한다. 전용 계정(pg_monitor)은 운영 DB에 수동 SQL이 필요해 후속 과제로 남긴다.
  - 새 이미지(socket-proxy·alloy 포함)의 취약점 스캔은 하지 않았다. 버전만 고정했다.
- 운영 반영 시 postgres가 재생성된다(`command` 변경). 병합하면 backend-cd의 `deploy.sh`(`up -d api`)가 의존 서비스까지 재생성하므로 병합은 서버가 꺼진 18시 이후에 한다. 절차는 `docs/runbook/observability.md`.
- Grafana는 데이터소스 provisioning을 기동할 때만 읽어, 반영 후 `docker compose restart grafana`가 필요하다.
- 이체 트래픽이 거의 없어 5xx 한 건만으로 5%를 넘을 수 있다. 오알림이 많으면 최소 요청 수 조건을 검토한다.
- 18:00에 서버가 꺼질 때 발생 중이던 알림은 해제 메일이 오지 않는다. 꺼진 동안은 알림도 없다(ADR 0011).
- 요청 단위 상관 ID는 없다. 로그와 지표는 시각으로 맞춰 본다.

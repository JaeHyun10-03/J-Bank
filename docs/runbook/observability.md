# 관측 스택 운영 반영·점검·되돌리기

로그(loki·alloy·socket-proxy)와 알림(postgres-exporter·alertmanager)을 운영 EC2에 반영하는 절차다. 결정과 이유는 ADR 0014.
명령은 모두 호스트의 `/opt/jbank/infra/compose`에서 `ec2-user`로 실행한다(SSM 세션). 아래 `dc`는 `docker compose -f docker-compose.prod.yml`이다.

메모리 단위는 MiB다. `MemAvailable`(kB) ÷ 1024로 읽는다.

## 1. 병합 시점

- PR은 서버가 꺼진 **18:00(KST) 이후**에 병합한다.
- 병합하면 backend-cd가 이미지를 만들고, 다음 날 부팅 때 `sync-latest.sh`가 `deploy.sh`(`up -d api`)를 실행한다. postgres 설정(`log_lock_waits`)이 바뀌어 postgres가 재생성된다. 배치 전, 사용자가 없는 시각이다.
- 이 단계에서는 새 서비스가 생기지 않는다. 기존 prometheus는 새 설정을 읽어도 정상 기동한다(없는 alertmanager·exporter 대상은 연결 실패만 남는다).

## 2. 반영 (다음 날 운영 시간 중)

1. 반영 전 기록: `free -m`, `grep MemAvailable /proc/meminfo`, `docker stats --no-stream`.
2. 메일 설정 파일을 만든다. 값은 직접 입력하고 저장소·채팅에 남기지 않는다.

   ```bash
   install -m 600 /dev/null .env.alertmanager
   vi .env.alertmanager
   ```

   ```text
   ALERT_EMAIL_TO=받을 주소
   SMTP_FROM=보내는 Gmail 주소
   SMTP_SMARTHOST=smtp.gmail.com:587
   SMTP_AUTH_USERNAME=보내는 Gmail 주소
   SMTP_AUTH_PASSWORD=Gmail 앱 비밀번호(2단계 인증 후 발급)
   ```

3. `.env`에 `COMPOSE_PROFILES=logs`를 추가한다. api는 `.env`를 통째로 받으므로 다음 `up -d`에서 api도 재생성된다(수 초 중단). 트래픽이 적을 때 한다.
4. `dc up -d` → `dc restart grafana`(provisioning은 기동 때만 읽는다).
5. Grafana(`grafana.j-bank.site`) → Connections → Data sources에서 Prometheus·Loki의 "Save & test"가 성공하는지 본다. Alertmanager 데이터소스는 Grafana가 상태 확인을 지원하지 않으므로("Plugin unavailable") Alerting → Alert groups에서 데이터소스를 Alertmanager로 골라 목록이 열리는지로 확인한다. Explore에서 `{container="compose-api-1"}`, `{container="compose-postgres-1"}`이 조회되는지 본다.

## 3. 메모리 점검

1. 반영 5분 뒤 유휴 가용 메모리를 잰다. **600MiB 미만이면 4절로 로그 수집을 끈다.**
2. 배치 중 최저값을 잰다. 한 창에서 표본을 뜨고 다른 창에서 원장 대사 배치(업무 데이터는 읽기만 함)를 1회 돌린다.

   ```bash
   while sleep 1; do echo "$(date +%T) $(( $(awk '/MemAvailable/{print $2}' /proc/meminfo) / 1024 ))"; done | tee /tmp/mem.log
   ```

   ```bash
   bash run-batch.sh ledgerReconciliationJob
   ```

   - 표본 중 **150MiB 미만이 보이면 즉시** `dc stop loki alloy socket-proxy`를 실행한다.
   - 서버가 응답하지 않으면 EC2를 중지·시작해 복구한다(커밋된 데이터는 WAL로 복구된다).
   - 최저값이 150MiB 미만이었으면 4절로 로그 수집을 끈다.
3. 측정값(유휴, 배치 중 최저, `docker stats`)을 작업 기록에 남긴다. `docker stats`에서 상한 대비 사용률도 본다. 로컬에서 socket-proxy가 16MiB 중 약 12MiB(75%)를 썼으므로, 운영에서 상한에 붙어 재시작이 반복되면(`docker inspect -f '{{.RestartCount}}'`) 상한을 올린다.

## 4. 로그 수집 끄기 (기준 미달 시)

1. `.env`에서 `COMPOSE_PROFILES=logs`를 지운다.
2. `dc stop loki alloy socket-proxy`.
3. 이후 `dc up -d`를 해도 세 서비스는 올라오지 않는다(`.env`가 바뀌어 이때 api가 재생성된다). 알림(postgres-exporter·alertmanager)은 그대로 동작한다. Grafana의 Loki 데이터소스는 연결 실패로 남는다.

## 5. 알림 확인

1. 테스트 알림을 넣고 받은 메일함에서 발생 메일을 확인한다.

   ```bash
   dc exec alertmanager amtool alert add RunbookTest target=manual --annotation='summary="관측 스택 메일 확인"' --end="$(date -u -d '+2 min' +%FT%TZ)" --alertmanager.url=http://localhost:9093
   ```

2. 2분 뒤 끝나고 다음 묶음 주기(최대 5분)에 해제 메일이 온다.
3. Grafana의 Alerting → Alertmanager 데이터소스 화면에서 발생 중인 알림을 볼 수 있다.
4. 첫 부팅 반영(1절) 때 `pg_up`이 0이었던 구간 길이를 Prometheus에서 확인해 기록한다. 2분을 넘으면 부팅 때마다 다운 오알림이 온다.

## 6. 되돌리기

호스트에서만 되돌리면 다음 배포·부팅의 `git reset`에 덮인다. main에서 되돌린다.

1. 되돌리는 revert 커밋을 **18:00 이후** 병합한다(다음 부팅 때 postgres가 원래 설정으로 재생성된다).
2. 다음 부팅에서 `sync-latest.sh`가 끝난 뒤(`journalctl -u jbank-boot`로 확인) `dc up -d --remove-orphans`로 새 서비스를 지운다.
3. `dc restart grafana` 후 Grafana에서 Loki·Alertmanager 데이터소스를 삭제한다(provisioning에서 빠져도 DB에 남는다).
4. `.env.alertmanager`를 지우고 `.env`의 `COMPOSE_PROFILES`를 지운다.

## 7. 알려진 한계

- postgres의 여러 줄 로그(`deadlock detected` 아래 `DETAIL`, `Process NNNN: <쿼리>`, `STATEMENT`)는 Loki에 줄마다 따로 들어간다. 교착을 볼 때는 `{container="compose-postgres-1"} |~ "deadlock detected|DETAIL|Process [0-9]+:|STATEMENT"`처럼 함께 조회하고 시각으로 묶어 읽는다.

- 18:00에 서버가 꺼질 때 발생 중이던 알림은 해제 메일이 오지 않는다.
- 이체 트래픽이 적어 5xx 한 건으로도 5%를 넘을 수 있다.
- alloy가 침해되면 inspect로 api 환경변수(비밀값)를 읽을 수 있다(ADR 0014, 수용).

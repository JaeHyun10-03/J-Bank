# 이체 교착 재현 실험

정렬 없는 락 순서가 양방향 동시 이체에서 교착을 만들고, 그 교착이 알림 메일·DB 로그·지표로 드러나며, 락 순서 정렬과 현재 락 모드로 사라지는 것을 같은 조건의 수치로 남기는 절차다.
운영 장애가 아니라 **일부러 재현한 실험**이다. 작업 명세는 `.claude/tasks/transfer-deadlock-experiment/task.md`, 결과는 `perf/results/deadlock/`에 있다.

## 1. 조건과 설정

| 조건 | 설정값 `JBANK_TRANSFER_LOCK_STRATEGY` | 락 순서 | 락 모드 |
| --- | --- | --- | --- |
| ① | `unsorted-for-update` | 요청 방향(송금 → 수신) | 둘 다 `FOR UPDATE` |
| ② | `sorted-for-update` | 계좌번호(OTP 완료는 계좌 ID) 오름차순 | 둘 다 `FOR UPDATE` |
| ③ | `current`(기본, 미설정·빈 값과 같음) | 같은 정렬 | 송금 `FOR NO KEY UPDATE`, 수신 `FOR KEY SHARE`(ADR 0012) |

- 즉시 이체와 OTP 완료 두 경로 모두 이 설정을 따른다. 실험 부하는 즉시 이체만 쓴다.
- 알 수 없는 값이면 api가 기동하지 않는다. 기동 로그에 `이체 락 전략: <값>`(INFO)이 남고, current가 아니면 WARN도 남는다.
- 교착으로 실패한 요청은 지금처럼 500·`COMMON_006_INTERNAL_ERROR`다. 재시도와 전용 코드는 이 실험의 범위 밖이다.

## 2. 회차 절차(`perf/run-deadlock.sh`가 하는 일)

1. 전략을 적용한다. api를 재생성하고 기동 로그의 전략을 확인한다.
   - 로컬은 셸 변수로 바꾼다.
   - 운영은 `.env`의 그 한 줄만 바꾼다. `current`는 줄을 지운다. 이 변경은 `runuser -u ec2-user -- flock /var/lib/jbank/deploy.lock` 안에서 한다.
2. 미반영 입금이 0인지 확인하고 `VACUUM (ANALYZE) accounts`를 실행한다.
3. 워밍업을 한다(20초, VU 5).
4. 미반영 입금 0을 다시 확인하고, 10초 뒤 기준값을 `before.json`에 기록한다. 기록 항목은 교착 카운터, 두 계좌의 완료 거래·원장 건수, 잔액 합, 거래 테이블 행 수다.
5. 측정한다(k6 `constant-vus`, 60초, 요청 타임아웃 30초).
   - 같은 고객의 두 계좌 사이에서 1,000원을 보낸다.
   - 방향은 `(__VU + __ITER) % 2`로 정한다.
   - 운영은 이 동안 1초 간격으로 `MemAvailable` 표본을 뜬다.
6. 10초 뒤 미반영 입금 0을 확인하고 종료값을 `after.json`에 기록한다. 락 대기(`db.lock.wait`)와 회차 시작 이후의 postgres 로그도 저장한다.
7. 조건 ①만 추가 단계가 있다.
   - 로컬은 Loki에서 `deadlock detected`와 `DETAIL`을 조회해 `loki.json`에 남긴다.
   - 그다음 알림 해제를 기다린다(상한 15분).
     - 로컬은 mailpit에 `[RESOLVED] DeadlockDetected` 메일이 오면 해제로 본다.
     - 운영은 Alertmanager·Prometheus에서 알림이 사라진 뒤 6분을 더 기다린다.
   - 상한에 닿으면 "해제 미확인"으로 기록하고 다음 회차로 간다. 운영의 마지막 회차 뒤에는 기다리지 않는다.
8. `result.json`에 결과를 기록한다.
   - 정상: `완료`
   - 중단: `무응답` / `시간 초과` / `측정 미실행(사유)`
   - 조건 ①은 해제 상태도 함께 남긴다.

결과 폴더는 `perf/results/deadlock/<local|prod>/<조건>-vu<N>-r<회차>/`이다. 계좌번호·비밀번호·토큰은 파일에 쓰지 않는다. 계좌는 ID로만 남기고, k6 요약의 `setup_data`는 비운다.

## 3. 로컬 실험(18회)

1. 로컬 `docker`가 지워진 OrbStack을 가리키면 Docker Desktop CLI를 앞에 둔다.

   ```bash
   export PATH=/Applications/Docker.app/Contents/Resources/bin:$PATH
   ```

2. 현재 코드로 이미지를 만들고 core·api·observability를 띄운다.

   ```bash
   cd infra/compose && COMPOSE_PROFILES=core,api,observability docker compose build api && COMPOSE_PROFILES=core,api,observability docker compose up -d
   ```

3. 테스트 고객 1명과 ACTIVE 계좌 2개(각 1억 원)를 가입·개설·입금 API로 만든다.
   - `perf/prepare-accounts.py`의 `register`·`login`·`open_account`·`deposit`을 쓴다.
   - 비밀번호는 저장소 밖에만 둔다.
4. 회차마다 다음 명령을 실행한다.

   ```bash
   LOGIN_ID=<로그인ID> PASSWORD=<비밀번호> perf/run-deadlock.sh local <조건> <VU> <회차>
   ```

5. 순서
   - 1회는 ①②③, 2회는 ②③①, 3회는 ③①② 순서로 돈다.
   - 각 조건 안에서는 VU 50 → 100이다.
   - 소요 시간은 약 2시간 30분이다. ① 6회 × 최대 약 15분이 대부분이다.

## 4. 운영 실험(6회)

### 4-1. 실험일 전 확인(에이전트)

- 사용자 자격증명으로 읽기 전용 명령이 되는지 확인한다.
  - SSM: `aws ssm send-command ... --parameters 'commands=["echo ok"]'`
  - 스케줄: `aws scheduler get-schedule --name jbank-dev-app-stop`
- 사용자가 할 일: 운영에 테스트 고객 1명을 가입하고, ACTIVE 계좌 2개를 열어 각각 1억 원 이상 입금한다. 에이전트는 운영 계정을 만들거나 비밀번호를 입력하지 않는다.

### 4-2. 실행(사용자, 14시 전 시작)

- 부팅 배치가 끝났는지 먼저 본다. `systemctl is-active jbank-boot`가 activating이 아니고, `/var/log/jbank/boot.log`의 마지막 `boot: 시작` 뒤에 `끝` 또는 `배치 건너뜀`이 있어야 한다.
- 사용자 터미널에서 실행한다. 비밀번호는 한 번 입력한다(`read -s`).

  ```bash
  INSTANCE_ID=<인스턴스ID> LOGIN_ID=<로그인ID> perf/run-deadlock.sh prod-all
  ```

- 순서는 ③(VU 50·100) → ②(VU 50·100) → ①(VU 50·100)이다. 소요 시간은 약 40분이다.
- 마지막 단계에서 설정을 복귀한다(5절).
- 진행 상황은 `perf/results/deadlock/prod/progress.log`로 본다.
- 알림 메일(DeadlockDetected 발생·해제, 그 밖의 5xx 비율·락 대기 알림)은 사용자가 Gmail에서 확인한다. 받은 시각은 회차 결과와 함께 기록한다. 측정을 마친 ① 회차가 없거나, 측정 직후 서버가 멈춰 메일을 못 받았으면 "미수신(사유)"으로 적고 다시 돌리지 않는다.

### 4-3. 무응답·시간 초과

- SSM 단계마다 대기 상한이 5분이다.
  - 그동안 `https://api.j-bank.site/readyz`가 한 번도 200을 주지 않으면 "무응답", 200을 주면 "시간 초과"다.
  - 측정 전에 멈추면 "측정 미실행(사유)"다.
- 스크립트는 그 회차를 기록하고 끝낸다. 남은 회차는 돌지 않는다. 끝난 회차는 다시 돌리지 않는다.
- 복구 순서(에이전트)
  1. 무응답이면 AWS CLI로 EC2를 중지·시작한다.
  2. 부팅 배치가 끝났는지 확인한다(4-2와 같은 기준). failed이면 실패한 잡을 기록한다.
  3. 전략 변수와 중지 스케줄 상태를 확인한다.
  4. 측정을 마친 회차였다면 미반영 입금 0을 확인하고 종료값을 SSM으로 기록한다. 그 회차의 불변식은 이 값으로 판정한다.
     - 다음 회차가 시작되면 거래 수가 바뀌므로, 재개 전에 한다.
     - 시간 초과도 원인을 확인하고 기록한 뒤 같은 방식으로 처리한다.
  5. 사용자가 남은 회차만 재개한다. 비밀번호는 다시 입력한다.

     ```bash
     INSTANCE_ID=<인스턴스ID> LOGIN_ID=<로그인ID> perf/run-deadlock.sh prod-all --from <조건>-vu<VU>
     ```

     직전 ① 회차가 "해제 미확인"이면 스크립트가 해제 확인부터 한다.

### 4-4. 18시를 넘길 때(그날만 연장)

17:40에 남은 일(남은 회차, FDS 관찰)이 있으면 에이전트가 중지 스케줄 정의를 보존한 채 State만 바꾼다.

```bash
aws scheduler get-schedule --name jbank-dev-app-stop > /tmp/stop-schedule.json
jq 'del(.Arn, .CreationDate, .LastModificationDate) | .State = "DISABLED"' /tmp/stop-schedule.json > /tmp/stop-off.json
aws scheduler update-schedule --cli-input-json file:///tmp/stop-off.json
```

일이 끝나면 다시 켜고 EC2를 직접 중지한다. 저장본과 비교해 State 외 차이가 0인지 확인한다.

```bash
jq 'del(.Arn, .CreationDate, .LastModificationDate) | .State = "ENABLED"' /tmp/stop-schedule.json > /tmp/stop-on.json
aws scheduler update-schedule --cli-input-json file:///tmp/stop-on.json
diff <(jq -S 'del(.Arn, .CreationDate, .LastModificationDate)' /tmp/stop-schedule.json) \
     <(aws scheduler get-schedule --name jbank-dev-app-stop | jq -S 'del(.Arn, .CreationDate, .LastModificationDate)')
```

18시 자동 종료로 실험이 끊기면 `.env`에 실험 전략이 남은 채 다음 날 부팅된다. 재개할 때는 전략 변수와 스케줄 ENABLED 확인부터 한다.

## 5. 설정 복귀

- 정상 종료: `prod-all` 마지막 단계가 다음을 확인하고 `perf/results/deadlock/prod/restore.md`에 남긴다.
  - 전략 줄을 지우고 api를 재생성한다.
  - 기동 로그가 `이체 락 전략: current`인지 본다.
  - 받아 둔 비밀번호로 이체 1건을 보낸다.
  - 중지 스케줄 상태를 기록한다.
- 남은 회차 없이 중단: 역할을 나눈다.
  - 에이전트: SSM으로 줄 제거, api 재생성, 기동 로그, 스케줄을 확인한다.
  - 사용자: 이체 1건을 확인한다(비밀번호 `read -s`).

    ```bash
    INSTANCE_ID=<인스턴스ID> LOGIN_ID=<로그인ID> perf/run-deadlock.sh prod-restore-check
    ```

- 운영 조회는 `JBANK_TRANSFER_LOCK_STRATEGY` 줄과 컨테이너의 그 변수만 출력한다. `.env` 전체나 `docker inspect`는 출력하지 않는다.

## 6. 실험 당일 FDS 배치 관찰(에이전트, SSM)

운영 실험이 끝난 뒤 18시 전에 한다. 늦어지면 4-4에 따라 연장한다.

1. 마지막 완료 runDate가 실험일 전날인지 확인한다. 아니면 따라잡기 공백이 생기므로 실행하지 않고 사용자에게 알린다.

   ```sql
   SELECT max(p.parameter_value) FROM batch_job_execution_params p
     JOIN batch_job_execution e ON e.job_execution_id = p.job_execution_id
     JOIN batch_job_instance i ON i.job_instance_id = e.job_instance_id
    WHERE i.job_name = 'fdsDetectionJob' AND e.status = 'COMPLETED' AND p.parameter_name = 'runDate';
   ```

2. 1초 간격 `MemAvailable` 표본을 켠다(관측 스택 runbook 3절의 명령).
3. `select count(*) from suspicious_transactions`로 배치 전 개수를 센다.
4. `bash run-batch.sh fdsDetectionJob --run-date <실험일>`을 실행한다.
5. 배치 후 개수를 세고 표본을 요약한다. 기록 항목은 종료 상태, 소요 시간, 최저 가용 메모리, 새 의심 거래 수다.
6. 다음 부팅의 따라잡기는 그 날짜를 건너뛴다. 수동 실행 뒤 같은 날 생긴 방문자 이체는 검사에서 빠진다는 점을 보고서에 적는다.

## 7. 결과 정리

- 요약표는 스크립트로 만든다. 보고서 `perf/results/deadlock/README.md`의 `<!-- summary:<환경> -->` 사이에 출력을 그대로 넣는다.

  ```bash
  python3 perf/deadlock-summary.py local
  ```

- 보고서 표가 스크립트 출력과 같은지 확인한다(REQ-08).

  ```bash
  python3 perf/deadlock-summary.py local --check
  ```

- 불변식(REQ-05)
  - 잔액 합이 보존된다.
  - 원장 증가가 완료 증가 × 2다.
  - 음수 잔액이 0이다.
  - "실패 응답 중 반영 = 완료 증가 − 2xx"가 0 이상, 실패 응답 수 이하다.
- 무응답·시간 초과·측정 미실행 회차는 교착·집계 기준에서 빼고 결과 이름과 사유로 적는다.
- 비밀값 검사(task.md SEC-05)
  - 결과 리뷰 전에 실행하고 `logs/secret-check.md`에 남긴다.
  - 첫 브랜치(커밋 1~6)는 push 직전에도 같은 명령을 실행해 같은 파일에 덧붙인다.
  - 작업 기록 커밋 뒤 push 직전에 다시 실행한 결과는 progress.md와 최종 보고에 남긴다. 이미 커밋한 기록을 다시 고치지 않기 위해서다.
